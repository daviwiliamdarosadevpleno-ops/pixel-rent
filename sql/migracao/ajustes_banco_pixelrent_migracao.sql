-- ============================================================================
-- PIXEL RENT - AJUSTES CONSOLIDADOS DO BANCO
-- PostgreSQL 18 | Baseado no dump compartilhado em 17/11/2026
--
-- OBJETIVO
--   Aplicar correções pontuais sem reconstruir o banco.
--
-- MUITO IMPORTANTE - LEIA ANTES DE EXECUTAR
--   1) VERSAO DE MIGRACAO PARA O BANCO ORIGINAL pixelrent.
--   2) Execute SOMENTE depois de confirmar backup restauravel e revisar o diagnostico.
--   3) Execute UMA SECAO POR VEZ no DBeaver; nunca execute o arquivo inteiro.
--   4) Cada secao possui sua propria transacao BEGIN/COMMIT.
--   5) A secao 5 NAO altera senhas automaticamente; credenciais devem ser configuradas
--      manualmente para contas de demonstracao autorizadas.
--
-- ORDEM RECOMENDADA
--   1. Concorrência na devolução
--   2. View de locações ativas
--   3. Integridade dos subtipos de mídia
--   4. Validade de licenças e catálogo disponível
--   5. Função de autenticação
--   6. Auditoria com funcionário responsável e proteção do histórico
--
-- Seções 7 e 8 (locações atrasadas e manutenção) foram deixadas de fora por
-- serem opcionais e não necessárias para desbloquear a implementação inicial.
-- ============================================================================

-- Diagnóstico inicial. Confirme que está conectado à cópia de teste.
SELECT current_database() AS banco_atual,
       current_user AS usuario_conectado,
       current_setting('server_version') AS versao_postgresql;



-- ============================================================================
-- SECAO 1 [ALTA] - Serializar devolucoes da mesma locacao
--
-- Corrige a condicao de corrida em que duas devolucoes simultaneas de itens
-- diferentes da mesma locacao poderiam verificar a contagem de itens abertos
-- ao mesmo tempo. A linha de locacao e bloqueada antes da linha do item.
-- CREATE OR REPLACE preserva os privilegios existentes da procedure.
-- ============================================================================
BEGIN;


CREATE OR REPLACE PROCEDURE public.sp_registrar_devolucao(
    IN p_id_locacao INTEGER,
    IN p_id_midia INTEGER,
    IN p_com_defeito BOOLEAN DEFAULT FALSE,
    INOUT p_multa NUMERIC DEFAULT 0
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    v_prazo        DATE;
    v_data_dev     DATE;
    v_valor_diaria NUMERIC(8,2);
    v_dias_atraso  INTEGER;
    v_abertos      INTEGER;
BEGIN
    -- Serializa as devolucoes pertencentes a mesma locacao.
    PERFORM 1
      FROM public.locacao
     WHERE id_locacao = p_id_locacao
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Locacao % nao encontrada', p_id_locacao;
    END IF;

    -- Mantem a protecao individual do item.
    SELECT l.prazo_devolucao,
           il.data_devolucao,
           il.valor_diaria
      INTO v_prazo, v_data_dev, v_valor_diaria
      FROM public.item_locacao il
      JOIN public.locacao l ON l.id_locacao = il.id_locacao
     WHERE il.id_locacao = p_id_locacao
       AND il.id_midia = p_id_midia
     FOR UPDATE OF il;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Item nao encontrado (locacao %, midia %)',
            p_id_locacao, p_id_midia;
    END IF;

    IF v_data_dev IS NOT NULL THEN
        RAISE EXCEPTION 'Midia % ja foi devolvida na locacao %',
            p_id_midia, p_id_locacao;
    END IF;

    v_dias_atraso := GREATEST(CURRENT_DATE - v_prazo, 0);
    p_multa := v_dias_atraso * v_valor_diaria;

    -- A trigger trg_item_locacao_status_midia atualiza o status da midia.
    UPDATE public.item_locacao
       SET data_devolucao = CURRENT_DATE,
           multa = p_multa
     WHERE id_locacao = p_id_locacao
       AND id_midia = p_id_midia;

    -- Se foi devolvida com defeito, encaminha para manutencao.
    IF p_com_defeito THEN
        UPDATE public.midia
           SET status = 'MANUTENCAO'
         WHERE id_midia = p_id_midia;
    END IF;

    SELECT COUNT(*)
      INTO v_abertos
      FROM public.item_locacao
     WHERE id_locacao = p_id_locacao
       AND data_devolucao IS NULL;

    IF v_abertos = 0 THEN
        UPDATE public.locacao
           SET status = 'CONCLUIDA',
               valor_total = valor_total + (
                   SELECT COALESCE(SUM(multa), 0)
                     FROM public.item_locacao
                    WHERE id_locacao = p_id_locacao
               )
         WHERE id_locacao = p_id_locacao
           AND status <> 'CONCLUIDA';
    END IF;
END;
$$;

COMMIT;


-- ============================================================================
-- SECAO 2 [ALTA] - Expor os identificadores necessarios na view de devolucao
--
-- Acrescenta as colunas ao FINAL da view para respeitar a regra do
-- CREATE OR REPLACE VIEW. Isso permite ao atendente identificar o item/midia
-- sem receber SELECT direto em item_locacao.
-- ============================================================================
BEGIN;


CREATE OR REPLACE VIEW public.vw_locacoes_ativas AS
SELECT l.id_locacao,
       c.nome AS cliente,
       f.nome AS funcionario,
       j.titulo,
       p.nome AS plataforma,
       l.data_locacao,
       l.prazo_devolucao,
       l.status,
       il.id_item_locacao,
       il.id_midia,
       il.valor_diaria
  FROM public.locacao l
  JOIN public.cliente c       ON c.id_cliente = l.id_cliente
  JOIN public.funcionario f   ON f.id_funcionario = l.id_funcionario
  JOIN public.item_locacao il ON il.id_locacao = l.id_locacao
  JOIN public.midia m         ON m.id_midia = il.id_midia
  JOIN public.jogo j          ON j.id_jogo = m.id_jogo
  JOIN public.plataforma p    ON p.id_plataforma = m.id_plataforma
 WHERE il.data_devolucao IS NULL;

-- Reafirma a leitura da view pelos grupos previstos no modelo de acesso.
GRANT SELECT ON public.vw_locacoes_ativas TO grp_admin, grp_atendente;

COMMIT;


-- ============================================================================
-- SECAO 3 [MEDIA] - Impedir subtipo de midia incompatível
--
-- Adiciona tipo nas tabelas especializadas e FK composta para garantir:
--   midia.tipo = 'FISICA'  -> linha em midia_fisica com tipo 'FISICA'
--   midia.tipo = 'DIGITAL' -> linha em midia_digital com tipo 'DIGITAL'
--
-- IMPORTANTE: FK composta impede subtipo errado, mas nao obriga que toda
-- linha de midia tenha um subtipo. A aplicacao deve inserir a linha de midia
-- e seu subtipo na MESMA transacao JDBC. Uma constraint trigger diferida pode
-- ser considerada futuramente se o grupo quiser impor isso no banco.
-- ============================================================================
BEGIN;


DO $$
BEGIN
    -- Interrompe antes de migrar se os dados atuais ja estiverem inconsistentes.
    IF EXISTS (
        SELECT 1
          FROM public.midia m
          LEFT JOIN public.midia_fisica mf ON mf.id_midia = m.id_midia
          LEFT JOIN public.midia_digital md ON md.id_midia = m.id_midia
         WHERE m.tipo NOT IN ('FISICA', 'DIGITAL')
            OR (m.tipo = 'FISICA' AND (mf.id_midia IS NULL OR md.id_midia IS NOT NULL))
            OR (m.tipo = 'DIGITAL' AND (md.id_midia IS NULL OR mf.id_midia IS NOT NULL))
    ) THEN
        RAISE EXCEPTION
            'Migracao cancelada: existem midias sem subtipo ou com subtipo incompatível. Corrija os dados antes de aplicar a secao 3.';
    END IF;
END;
$$;

-- A FK composta precisa de uma chave UNIQUE com exatamente estas colunas.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conrelid = 'public.midia'::regclass
           AND conname = 'uq_midia_id_tipo'
    ) THEN
        ALTER TABLE public.midia
            ADD CONSTRAINT uq_midia_id_tipo UNIQUE (id_midia, tipo);
    END IF;
END;
$$;

ALTER TABLE public.midia_fisica
    ADD COLUMN IF NOT EXISTS tipo VARCHAR(10) NOT NULL DEFAULT 'FISICA';

ALTER TABLE public.midia_digital
    ADD COLUMN IF NOT EXISTS tipo VARCHAR(10) NOT NULL DEFAULT 'DIGITAL';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = 'public.midia_fisica'::regclass
           AND conname = 'ck_mf_tipo'
    ) THEN
        ALTER TABLE public.midia_fisica
            ADD CONSTRAINT ck_mf_tipo CHECK (tipo = 'FISICA');
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = 'public.midia_digital'::regclass
           AND conname = 'ck_md_tipo'
    ) THEN
        ALTER TABLE public.midia_digital
            ADD CONSTRAINT ck_md_tipo CHECK (tipo = 'DIGITAL');
    END IF;
END;
$$;

-- Substitui as FKs simples pelas FKs compostas.
ALTER TABLE public.midia_fisica
    DROP CONSTRAINT IF EXISTS fk_mf_midia;
ALTER TABLE public.midia_fisica
    DROP CONSTRAINT IF EXISTS fk_mf_midia_tipo;
ALTER TABLE public.midia_fisica
    ADD CONSTRAINT fk_mf_midia_tipo
    FOREIGN KEY (id_midia, tipo)
    REFERENCES public.midia (id_midia, tipo)
    ON DELETE CASCADE;

ALTER TABLE public.midia_digital
    DROP CONSTRAINT IF EXISTS fk_md_midia;
ALTER TABLE public.midia_digital
    DROP CONSTRAINT IF EXISTS fk_md_midia_tipo;
ALTER TABLE public.midia_digital
    ADD CONSTRAINT fk_md_midia_tipo
    FOREIGN KEY (id_midia, tipo)
    REFERENCES public.midia (id_midia, tipo)
    ON DELETE CASCADE;

COMMIT;


-- ============================================================================
-- SECAO 4 [ALTA] - Bloquear licenca digital vencida e corrigir catalogo
--
-- Depende da secao 3. A trigger agora verifica status, tipo e existencia do
-- subtipo. Para licenca digital, validade NULL significa sem data de expiracao.
-- O catalogo tambem exclui registros sem subtipo e licencas vencidas.
-- ============================================================================
BEGIN;


CREATE OR REPLACE FUNCTION public.fn_atualizar_status_midia()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    v_status VARCHAR(15);
    v_tipo   VARCHAR(10);
BEGIN
    IF TG_OP = 'INSERT' THEN
        SELECT m.status, m.tipo
          INTO v_status, v_tipo
          FROM public.midia m
         WHERE m.id_midia = NEW.id_midia
         FOR UPDATE;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'Midia % nao encontrada', NEW.id_midia;
        END IF;

        IF v_status IS DISTINCT FROM 'DISPONIVEL' THEN
            RAISE EXCEPTION 'Midia % nao esta disponivel (status atual: %)',
                NEW.id_midia, v_status;
        END IF;

        IF v_tipo = 'FISICA' THEN
            IF NOT EXISTS (
                SELECT 1 FROM public.midia_fisica
                 WHERE id_midia = NEW.id_midia
            ) THEN
                RAISE EXCEPTION 'Midia fisica % nao possui cadastro especializado',
                    NEW.id_midia;
            END IF;

        ELSIF v_tipo = 'DIGITAL' THEN
            IF NOT EXISTS (
                SELECT 1 FROM public.midia_digital
                 WHERE id_midia = NEW.id_midia
            ) THEN
                RAISE EXCEPTION 'Midia digital % nao possui cadastro de licenca',
                    NEW.id_midia;
            END IF;

            IF EXISTS (
                SELECT 1
                  FROM public.midia_digital
                 WHERE id_midia = NEW.id_midia
                   AND validade IS NOT NULL
                   AND validade < CURRENT_DATE
            ) THEN
                RAISE EXCEPTION 'Licenca da midia % esta vencida', NEW.id_midia;
            END IF;
        ELSE
            RAISE EXCEPTION 'Tipo de midia invalido para a midia %: %',
                NEW.id_midia, v_tipo;
        END IF;

        UPDATE public.midia
           SET status = 'ALUGADA'
         WHERE id_midia = NEW.id_midia;

    ELSIF TG_OP = 'UPDATE' THEN
        IF OLD.data_devolucao IS NULL AND NEW.data_devolucao IS NOT NULL THEN
            UPDATE public.midia
               SET status = 'DISPONIVEL'
             WHERE id_midia = NEW.id_midia;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE OR REPLACE VIEW public.vw_catalogo_disponivel AS
SELECT m.id_midia,
       j.titulo,
       g.nome AS genero,
       p.nome AS plataforma,
       m.tipo,
       m.valor_diaria,
       COALESCE(mf.codigo_barras, md.codigo_licenca) AS identificador
  FROM public.midia m
  JOIN public.jogo j ON j.id_jogo = m.id_jogo
  JOIN public.genero g ON g.id_genero = j.id_genero
  JOIN public.plataforma p ON p.id_plataforma = m.id_plataforma
  LEFT JOIN public.midia_fisica mf ON mf.id_midia = m.id_midia
  LEFT JOIN public.midia_digital md ON md.id_midia = m.id_midia
 WHERE m.status = 'DISPONIVEL'
   AND (
       (m.tipo = 'FISICA'
        AND mf.id_midia IS NOT NULL
        AND md.id_midia IS NULL)
       OR
       (m.tipo = 'DIGITAL'
        AND md.id_midia IS NOT NULL
        AND mf.id_midia IS NULL
        AND (md.validade IS NULL OR md.validade >= CURRENT_DATE))
   );

GRANT SELECT ON public.vw_catalogo_disponivel TO grp_admin, grp_atendente;

COMMIT;

-- NOTA DE REGRA DE NEGOCIO:
-- A coluna status aceita atualmente DISPONIVEL, ALUGADA e MANUTENCAO.
-- Na devolucao, a trigger retorna a midia a DISPONIVEL. Se uma licenca digital
-- vencer DURANTE a locacao, o catalogo a ocultara pela validade, embora a
-- coluna status possa ficar DISPONIVEL. Se o grupo quiser um estado explicito
-- LICENCA_VENCIDA, isso exigira alterar CHECK, diagramas e telas; nao foi feito
-- aqui para evitar ampliar o escopo sem aprovacao.


-- ============================================================================
-- SECAO 5 [ALTA] - Funcao de autenticacao para o Java
--
-- Nao sobrescreve senhas existentes. Primeiro confira os logins e o formato
-- atual de senha_hash. Depois atualize manualmente somente uma conta de teste
-- autorizada, usando o comando comentado mais abaixo.
-- ============================================================================
BEGIN;


CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

-- Confirma que pgcrypto disponibilizou crypt() e gen_salt() em public.
DO $$
BEGIN
    IF to_regprocedure('public.crypt(text,text)') IS NULL
       OR to_regprocedure('public.gen_salt(text,integer)') IS NULL THEN
        RAISE EXCEPTION
            'pgcrypto nao foi encontrado no schema public. Verifique o schema da extensao antes de continuar.';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.fn_autenticar_funcionario(
    p_login VARCHAR,
    p_senha TEXT
)
RETURNS TABLE (
    id_funcionario INTEGER,
    nome VARCHAR(100),
    perfil VARCHAR(15)
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
    SELECT f.id_funcionario, f.nome, f.perfil
      FROM public.funcionario f
     WHERE f.login = p_login
       AND f.ativo IS TRUE
       -- Ignora hashes antigos/de demonstracao que nao sejam bcrypt.
       AND CASE
             WHEN f.senha_hash LIKE '$2%' THEN
                 f.senha_hash = public.crypt(p_senha, f.senha_hash)
             ELSE FALSE
           END;
$$;

REVOKE ALL ON FUNCTION public.fn_autenticar_funcionario(VARCHAR, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.fn_autenticar_funcionario(VARCHAR, TEXT)
    TO grp_admin, grp_atendente;

COMMIT;

-- DIAGNOSTICO SEGURO: mostra logins e formato do hash, nunca a senha/hash.
SELECT id_funcionario,
       login,
       perfil,
       ativo,
       CASE WHEN senha_hash LIKE '$2%' THEN 'BCRYPT' ELSE 'HASH_A_ATUALIZAR' END AS situacao_hash
  FROM public.funcionario
 ORDER BY id_funcionario;

-- COMANDO MANUAL OPCIONAL - NAO DESCOMENTAR SEM AUTORIZACAO:
-- Troque LOGIN_DE_TESTE e SENHA_DE_TESTE por uma conta de demonstracao escolhida.
-- Confira antes que o WHERE identifica exatamente uma linha.
-- UPDATE public.funcionario
--    SET senha_hash = public.crypt('SENHA_DE_TESTE', public.gen_salt('bf', 12))
--  WHERE login = 'LOGIN_DE_TESTE';
-- Depois teste:
-- SELECT * FROM public.fn_autenticar_funcionario('LOGIN_DE_TESTE', 'SENHA_DE_TESTE');


-- ============================================================================
-- SECAO 6 [MEDIA] - Auditar INSERT/UPDATE/DELETE e identificar funcionario
--
-- O Java deve, APOS autenticar o funcionario e dentro da mesma transacao JDBC:
--   SELECT set_config('app.id_funcionario', '<id_funcionario>', true);
-- O terceiro argumento TRUE torna o valor local a transacao.
-- A aplicacao deve manter autoCommit=false durante set_config + operacao.
-- Se o contexto nao for definido, id_funcionario_acao fica NULL.
-- ============================================================================
BEGIN;


ALTER TABLE public.auditoria_locacao
    DROP CONSTRAINT IF EXISTS auditoria_locacao_operacao_check;

ALTER TABLE public.auditoria_locacao
    ADD CONSTRAINT auditoria_locacao_operacao_check
    CHECK (operacao IN ('INSERT', 'UPDATE', 'DELETE'));

ALTER TABLE public.auditoria_locacao
    ADD COLUMN IF NOT EXISTS id_funcionario_acao INTEGER;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conrelid = 'public.auditoria_locacao'::regclass
           AND conname = 'fk_auditoria_funcionario'
    ) THEN
        ALTER TABLE public.auditoria_locacao
            ADD CONSTRAINT fk_auditoria_funcionario
            FOREIGN KEY (id_funcionario_acao)
            REFERENCES public.funcionario(id_funcionario)
            ON DELETE SET NULL;
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.fn_auditar_locacao()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    v_func INTEGER := NULLIF(
        pg_catalog.current_setting('app.id_funcionario', TRUE), ''
    )::INTEGER;
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO public.auditoria_locacao
            (id_locacao, operacao, usuario_bd, id_funcionario_acao,
             dados_antigos, dados_novos)
        VALUES
            (NEW.id_locacao, 'INSERT', SESSION_USER, v_func,
             NULL, pg_catalog.to_jsonb(NEW));
        RETURN NEW;

    ELSIF TG_OP = 'UPDATE' THEN
        INSERT INTO public.auditoria_locacao
            (id_locacao, operacao, usuario_bd, id_funcionario_acao,
             dados_antigos, dados_novos)
        VALUES
            (OLD.id_locacao, 'UPDATE', SESSION_USER, v_func,
             pg_catalog.to_jsonb(OLD), pg_catalog.to_jsonb(NEW));
        RETURN NEW;

    ELSIF TG_OP = 'DELETE' THEN
        INSERT INTO public.auditoria_locacao
            (id_locacao, operacao, usuario_bd, id_funcionario_acao,
             dados_antigos, dados_novos)
        VALUES
            (OLD.id_locacao, 'DELETE', SESSION_USER, v_func,
             pg_catalog.to_jsonb(OLD), NULL);
        RETURN OLD;
    END IF;

    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS trg_auditar_locacao ON public.locacao;

CREATE TRIGGER trg_auditar_locacao
AFTER INSERT OR UPDATE OR DELETE ON public.locacao
FOR EACH ROW
EXECUTE FUNCTION public.fn_auditar_locacao();

-- Historico somente para leitura pelo grupo administrador.
-- A trigger grava como proprietario da funcao SECURITY DEFINER (postgres).
REVOKE ALL PRIVILEGES ON TABLE public.auditoria_locacao FROM PUBLIC, grp_admin;
GRANT SELECT ON TABLE public.auditoria_locacao TO grp_admin;

REVOKE ALL PRIVILEGES ON SEQUENCE public.auditoria_locacao_id_auditoria_seq
    FROM PUBLIC, grp_admin;

COMMIT;


-- ============================================================================
-- VERIFICACOES POS-APLICACAO (CONSULTAS SOMENTE LEITURA)
-- Execute depois das secoes, uma consulta por vez.
-- ============================================================================

-- 1) Conferir os subtipos e tipos da midia.
SELECT m.id_midia, m.tipo,
       (mf.id_midia IS NOT NULL) AS possui_subtipo_fisico,
       (md.id_midia IS NOT NULL) AS possui_subtipo_digital
  FROM public.midia m
  LEFT JOIN public.midia_fisica mf ON mf.id_midia = m.id_midia
  LEFT JOIN public.midia_digital md ON md.id_midia = m.id_midia
 ORDER BY m.id_midia;

-- 2) Conferir as colunas novas da view de locacoes ativas.
SELECT * FROM public.vw_locacoes_ativas LIMIT 5;

-- 3) Conferir a autenticacao depois de configurar uma conta de demonstracao.
-- SELECT * FROM public.fn_autenticar_funcionario('LOGIN_DE_TESTE', 'SENHA_DE_TESTE');

-- 4) Conferir a estrutura de auditoria.
SELECT column_name, data_type
  FROM information_schema.columns
 WHERE table_schema = 'public'
   AND table_name = 'auditoria_locacao'
 ORDER BY ordinal_position;

-- 5) Conferir os privilegios de leitura do historico.
SELECT
    has_table_privilege('usr_admin_teste', 'public.auditoria_locacao', 'SELECT') AS admin_pode_ler,
    has_table_privilege('usr_admin_teste', 'public.auditoria_locacao', 'INSERT') AS admin_pode_inserir_diretamente,
    has_table_privilege('usr_admin_teste', 'public.auditoria_locacao', 'UPDATE') AS admin_pode_alterar,
    has_table_privilege('usr_admin_teste', 'public.auditoria_locacao', 'DELETE') AS admin_pode_excluir;

-- Resultado esperado para auditoria:
-- admin_pode_ler = true; os outros tres campos = false.
