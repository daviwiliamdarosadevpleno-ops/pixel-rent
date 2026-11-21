-- PIXEL RENT - TESTE FUNCIONAL CONTROLADO (VERSAO REVISADA)
-- PostgreSQL 18.x | Executar primeiro em pixelrent_restore e depois em pixelrent
--
-- SEGURANCA:
-- 1) Este teste inicia uma transacao e termina com ROLLBACK.
-- 2) Os INSERTs/UPDATEs e registros de auditoria do teste serao desfeitos.
-- 3) As sequences do PostgreSQL podem avancar mesmo com ROLLBACK; isso e normal.
-- 4) Se ocorrer SQL Error, execute ROLLBACK; em uma nova execucao antes de continuar.
-- 5) Nao substitua o ROLLBACK final por COMMIT.
--
-- CENARIOS:
-- - Criacao de dados temporarios e subtipos fisico/digital.
-- - Rejeicao de licenca digital vencida e ocultacao no catalogo.
-- - Locacao de uma midia fisica e uma digital valida.
-- - Atualizacao de status para ALUGADA.
-- - Rejeicao de segunda locacao da mesma midia.
-- - Devolucao parcial sem defeito, com multa e locacao ainda ATIVA.
-- - Rejeicao de devolucao duplicada.
-- - Devolucao final com defeito, status MANUTENCAO e locacao CONCLUIDA.
-- - Conferencia do valor total e da auditoria associada ao funcionario.
--
-- IMPORTANTE: diferentemente do roteiro anterior, uma falha inesperada gera SQL Error
-- e nao e ocultada por um bloco EXCEPTION externo que apenas imprime NOTICE.

BEGIN;

DO $$
DECLARE
    v_sufixo          TEXT := to_char(clock_timestamp(), 'YYYYMMDDHH24MISSMS');
    v_cliente         INTEGER;
    v_funcionario     INTEGER;
    v_genero          INTEGER;
    v_plataforma      INTEGER;
    v_jogo            INTEGER;
    v_midia_fisica    INTEGER;
    v_midia_digital   INTEGER;
    v_midia_vencida   INTEGER;
    v_locacao         INTEGER;
    v_locacao_teste   INTEGER;
    v_multa           NUMERIC(8,2);
    v_status          VARCHAR(15);
    v_status_locacao  VARCHAR(15);
    v_valor_total     NUMERIC(10,2);
    v_qtd_abertos     INTEGER;
    v_qtd_auditoria_insert INTEGER;
    v_qtd_auditoria_update INTEGER;
    v_qtd_auditoria_delete INTEGER;
    v_locacao_delete INTEGER;
    v_rejeitou        BOOLEAN;
    v_erro            TEXT;
BEGIN
    IF current_database() NOT IN ('pixelrent_restore', 'pixelrent') THEN
        RAISE EXCEPTION 'Conexao incorreta: este roteiro so pode ser executado em pixelrent_restore ou pixelrent. Banco atual: %', current_database();
    END IF;

    RAISE NOTICE 'Iniciando teste funcional Pixel Rent. Identificador: %', v_sufixo;

    -- 1. Registros-base temporarios.
    INSERT INTO public.cliente (nome, cpf, telefone, email, ativo)
    VALUES (
        'CLIENTE TESTE ' || v_sufixo,
        right(v_sufixo, 11),
        '(00) 00000-0000',
        'cliente.' || v_sufixo || '@example.invalid',
        TRUE
    )
    RETURNING id_cliente INTO v_cliente;

    INSERT INTO public.funcionario
        (nome, cpf, telefone, email, login, senha_hash, perfil, ativo)
    VALUES (
        'FUNCIONARIO TESTE ' || v_sufixo,
        right((v_sufixo::BIGINT + 1)::TEXT, 11),
        '(00) 00000-0000',
        'funcionario.' || v_sufixo || '@example.invalid',
        'teste_' || v_sufixo,
        'HASH_FICTICIO_APENAS_PARA_TESTE',
        'ADMINISTRADOR',
        TRUE
    )
    RETURNING id_funcionario INTO v_funcionario;

    INSERT INTO public.genero (nome)
    VALUES ('GENERO TESTE ' || v_sufixo)
    RETURNING id_genero INTO v_genero;

    INSERT INTO public.plataforma (nome)
    VALUES ('PLATAFORMA TESTE ' || v_sufixo)
    RETURNING id_plataforma INTO v_plataforma;

    INSERT INTO public.jogo
        (titulo, ano_lancamento, classificacao_indicativa, id_genero)
    VALUES
        ('JOGO TESTE ' || v_sufixo, 2025, 'LIVRE', v_genero)
    RETURNING id_jogo INTO v_jogo;

    INSERT INTO public.jogo_plataforma (id_jogo, id_plataforma)
    VALUES (v_jogo, v_plataforma);

    -- O contexto de auditoria fica limitado a esta transacao.
    PERFORM pg_catalog.set_config('app.id_funcionario', v_funcionario::TEXT, TRUE);

    -- 2. Criar midia fisica.
    INSERT INTO public.midia
        (id_jogo, id_plataforma, tipo, status, valor_diaria)
    VALUES (v_jogo, v_plataforma, 'FISICA', 'DISPONIVEL', 10.00)
    RETURNING id_midia INTO v_midia_fisica;

    INSERT INTO public.midia_fisica
        (id_midia, codigo_barras, estado_conservacao)
    VALUES (v_midia_fisica, 'B-' || v_sufixo, 'BOM');

    -- 3. Criar midia digital valida.
    INSERT INTO public.midia
        (id_jogo, id_plataforma, tipo, status, valor_diaria)
    VALUES (v_jogo, v_plataforma, 'DIGITAL', 'DISPONIVEL', 15.00)
    RETURNING id_midia INTO v_midia_digital;

    INSERT INTO public.midia_digital
        (id_midia, codigo_licenca, validade)
    VALUES (v_midia_digital, 'LIC-' || v_sufixo, CURRENT_DATE + 365);

    -- 4. Criar midia digital com licenca vencida.
    INSERT INTO public.midia
        (id_jogo, id_plataforma, tipo, status, valor_diaria)
    VALUES (v_jogo, v_plataforma, 'DIGITAL', 'DISPONIVEL', 12.00)
    RETURNING id_midia INTO v_midia_vencida;

    INSERT INTO public.midia_digital
        (id_midia, codigo_licenca, validade)
    VALUES (v_midia_vencida, 'VENC-' || v_sufixo, CURRENT_DATE - 1);

    IF EXISTS (
        SELECT 1 FROM public.vw_catalogo_disponivel
        WHERE id_midia = v_midia_vencida
    ) THEN
        RAISE EXCEPTION 'FALHA: midia digital com licenca vencida apareceu no catalogo.';
    END IF;
    RAISE NOTICE 'ETAPA 1 OK: subtipos criados e licenca vencida oculta do catalogo.';

    -- 5. Tentar alugar a midia digital vencida: a operacao precisa ser rejeitada
    -- especificamente pela validacao da validade da licenca.
    v_rejeitou := FALSE;
    v_erro := NULL;
    BEGIN
        v_locacao_teste := NULL;
        CALL public.sp_realizar_locacao(
            v_cliente, v_funcionario, ARRAY[v_midia_vencida], 1, v_locacao_teste
        );
    EXCEPTION WHEN OTHERS THEN
        v_rejeitou := TRUE;
        GET STACKED DIAGNOSTICS v_erro = MESSAGE_TEXT;
    END;

    IF NOT v_rejeitou THEN
        RAISE EXCEPTION 'FALHA: o banco aceitou aluguel de uma midia com licenca vencida.';
    END IF;
    IF position('Licenca da midia' IN COALESCE(v_erro, '')) = 0 THEN
        RAISE EXCEPTION 'A locacao foi rejeitada por motivo inesperado: %', v_erro;
    END IF;
    RAISE NOTICE 'ETAPA 2 OK: licenca vencida rejeitada: %', v_erro;

    -- 6. Alugar uma midia fisica e uma digital valida.
    v_locacao := NULL;
    CALL public.sp_realizar_locacao(
        v_cliente,
        v_funcionario,
        ARRAY[v_midia_fisica, v_midia_digital],
        1,
        v_locacao
    );

    IF v_locacao IS NULL THEN
        RAISE EXCEPTION 'FALHA: a procedure nao retornou o id da locacao.';
    END IF;

    SELECT status INTO v_status
    FROM public.midia WHERE id_midia = v_midia_fisica;
    IF v_status <> 'ALUGADA' THEN
        RAISE EXCEPTION 'Midia fisica deveria estar ALUGADA; status: %', v_status;
    END IF;

    SELECT status INTO v_status
    FROM public.midia WHERE id_midia = v_midia_digital;
    IF v_status <> 'ALUGADA' THEN
        RAISE EXCEPTION 'Midia digital deveria estar ALUGADA; status: %', v_status;
    END IF;

    SELECT COUNT(*) INTO v_qtd_abertos
    FROM public.item_locacao
    WHERE id_locacao = v_locacao AND data_devolucao IS NULL;
    IF v_qtd_abertos <> 2 THEN
        RAISE EXCEPTION 'Esperados 2 itens em aberto; encontrados: %', v_qtd_abertos;
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.vw_catalogo_disponivel
        WHERE id_midia IN (v_midia_fisica, v_midia_digital)
    ) THEN
        RAISE EXCEPTION 'FALHA: uma midia alugada continua aparecendo no catalogo.';
    END IF;
    RAISE NOTICE 'ETAPA 3 OK: locacao % criada; midias alugadas e ocultas do catalogo.', v_locacao;

    -- 7. Tentar alugar novamente a midia digital ja alugada.
    v_rejeitou := FALSE;
    BEGIN
        v_locacao_teste := NULL;
        CALL public.sp_realizar_locacao(
            v_cliente, v_funcionario, ARRAY[v_midia_digital], 1, v_locacao_teste
        );
    EXCEPTION WHEN OTHERS THEN
        v_rejeitou := TRUE;
        GET STACKED DIAGNOSTICS v_erro = MESSAGE_TEXT;
    END;

    IF NOT v_rejeitou THEN
        RAISE EXCEPTION 'FALHA: o banco aceitou uma segunda locacao da mesma midia.';
    END IF;
    SELECT status INTO v_status
    FROM public.midia WHERE id_midia = v_midia_digital;
    IF v_status <> 'ALUGADA' THEN
        RAISE EXCEPTION 'A midia deveria continuar ALUGADA; status: %', v_status;
    END IF;
    RAISE NOTICE 'ETAPA 4 OK: segunda locacao da mesma midia foi rejeitada.';

    -- 8. Simular atraso de dois dias.
    UPDATE public.locacao
    SET data_locacao = CURRENT_DATE - 4,
        prazo_devolucao = CURRENT_DATE - 2
    WHERE id_locacao = v_locacao;

    -- 9. Devolucao parcial sem defeito: multa esperada 2 x R$ 10,00.
    v_multa := 0;
    CALL public.sp_registrar_devolucao(
        v_locacao, v_midia_fisica, FALSE, v_multa
    );

    IF v_multa <> 20.00 THEN
        RAISE EXCEPTION 'Multa fisica esperada: 20,00; recebida: %', v_multa;
    END IF;

    SELECT status INTO v_status
    FROM public.midia WHERE id_midia = v_midia_fisica;
    IF v_status <> 'DISPONIVEL' THEN
        RAISE EXCEPTION 'Midia sem defeito deveria voltar a DISPONIVEL; status: %', v_status;
    END IF;

    SELECT status INTO v_status_locacao
    FROM public.locacao WHERE id_locacao = v_locacao;
    IF v_status_locacao <> 'ATIVA' THEN
        RAISE EXCEPTION 'Locacao deveria continuar ATIVA apos devolucao parcial; status: %', v_status_locacao;
    END IF;

    SELECT COUNT(*) INTO v_qtd_abertos
    FROM public.item_locacao
    WHERE id_locacao = v_locacao AND data_devolucao IS NULL;
    IF v_qtd_abertos <> 1 THEN
        RAISE EXCEPTION 'Deveria restar 1 item aberto; encontrados: %', v_qtd_abertos;
    END IF;
    RAISE NOTICE 'ETAPA 5 OK: devolucao parcial, multa R$ % e locacao ainda ATIVA.', v_multa;

    -- 10. A devolucao duplicada da midia fisica precisa ser rejeitada.
    v_rejeitou := FALSE;
    BEGIN
        v_multa := 0;
        CALL public.sp_registrar_devolucao(
            v_locacao, v_midia_fisica, FALSE, v_multa
        );
    EXCEPTION WHEN OTHERS THEN
        v_rejeitou := TRUE;
        GET STACKED DIAGNOSTICS v_erro = MESSAGE_TEXT;
    END;
    IF NOT v_rejeitou THEN
        RAISE EXCEPTION 'FALHA: o banco aceitou uma segunda devolucao da mesma midia.';
    END IF;
    RAISE NOTICE 'ETAPA 6 OK: devolucao duplicada foi rejeitada.';

    -- 11. Devolucao final com defeito: multa esperada 2 x R$ 15,00.
    v_multa := 0;
    CALL public.sp_registrar_devolucao(
        v_locacao, v_midia_digital, TRUE, v_multa
    );

    IF v_multa <> 30.00 THEN
        RAISE EXCEPTION 'Multa digital esperada: 30,00; recebida: %', v_multa;
    END IF;

    SELECT status INTO v_status
    FROM public.midia WHERE id_midia = v_midia_digital;
    IF v_status <> 'MANUTENCAO' THEN
        RAISE EXCEPTION 'Midia com defeito deveria ir para MANUTENCAO; status: %', v_status;
    END IF;

    SELECT status, valor_total
    INTO v_status_locacao, v_valor_total
    FROM public.locacao WHERE id_locacao = v_locacao;

    IF v_status_locacao <> 'CONCLUIDA' THEN
        RAISE EXCEPTION 'Locacao deveria estar CONCLUIDA; status: %', v_status_locacao;
    END IF;
    IF v_valor_total <> 75.00 THEN
        RAISE EXCEPTION 'Valor total esperado: 75,00; encontrado: %', v_valor_total;
    END IF;

    -- A auditoria deve identificar o funcionario temporario nas operacoes
    -- geradas pela locacao e pelas devolucoes (contexto da transacao).
    SELECT
        COUNT(*) FILTER (WHERE operacao = 'INSERT'),
        COUNT(*) FILTER (WHERE operacao = 'UPDATE')
    INTO v_qtd_auditoria_insert, v_qtd_auditoria_update
    FROM public.auditoria_locacao
    WHERE id_locacao = v_locacao
      AND id_funcionario_acao = v_funcionario;

    IF v_qtd_auditoria_insert < 1 OR v_qtd_auditoria_update < 2 THEN
        RAISE EXCEPTION
            'Auditoria incompleta para locacao %: INSERT=%; UPDATE=%',
            v_locacao, v_qtd_auditoria_insert, v_qtd_auditoria_update;
    END IF;

    RAISE NOTICE 'ETAPA 7 OK: devolucao final, multa R$ %, midia em MANUTENCAO.', v_multa;
    RAISE NOTICE 'ETAPA 8 OK: locacao CONCLUIDA, total R$ %. Auditoria: INSERT=%; UPDATE=%.',
        v_valor_total, v_qtd_auditoria_insert, v_qtd_auditoria_update;

    -- 12. Testar especificamente o evento DELETE da trigger de auditoria.
    -- Esta locacao temporaria nao possui itens, para nao interferir no fluxo acima.
    INSERT INTO public.locacao
        (id_cliente, id_funcionario, data_locacao, prazo_devolucao, status, valor_total)
    VALUES
        (v_cliente, v_funcionario, CURRENT_DATE, CURRENT_DATE + 1, 'ATIVA', 0)
    RETURNING id_locacao INTO v_locacao_delete;

    DELETE FROM public.locacao
    WHERE id_locacao = v_locacao_delete;

    SELECT COUNT(*) INTO v_qtd_auditoria_delete
    FROM public.auditoria_locacao
    WHERE id_locacao = v_locacao_delete
      AND operacao = 'DELETE'
      AND id_funcionario_acao = v_funcionario;

    IF v_qtd_auditoria_delete <> 1 THEN
        RAISE EXCEPTION
            'Esperado exatamente 1 registro de auditoria DELETE para locacao temporaria %; encontrados: %',
            v_locacao_delete, v_qtd_auditoria_delete;
    END IF;

    RAISE NOTICE 'ETAPA 9 OK: evento DELETE foi registrado com funcionario %.', v_funcionario;
    RAISE NOTICE 'RESULTADO: TODOS OS TESTES FUNCIONAIS PASSARAM.';
END;
$$;

-- Desfaz todos os dados temporarios e auditorias criados neste teste.
ROLLBACK;

SELECT 'Teste funcional encerrado com ROLLBACK. Nenhum registro de teste foi mantido.' AS resultado;
