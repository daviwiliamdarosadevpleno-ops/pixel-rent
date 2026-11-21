-- PIXEL RENT - Aplicacao da correcao de seguranca em producao
-- PostgreSQL 18.x
-- Executar SOMENTE em pixelrent, conectado como postgres.
-- A mesma definicao foi aplicada e testada em pixelrent_restore:
-- teste funcional (9 etapas) + teste de protecao contra tabelas temporarias
-- executado como usr_atendente_teste.
-- Preserva a assinatura, a logica de negocio e os privilegios existentes.

BEGIN;

DO $precheck$
BEGIN
    IF current_database() <> 'pixelrent' THEN
        RAISE EXCEPTION
            'ABORTADO: banco atual e %, mas esta migracao so pode rodar em pixelrent.',
            current_database();
    END IF;

    IF current_user <> 'postgres' THEN
        RAISE EXCEPTION
            'ABORTADO: usuario atual e %, mas esta migracao deve ser executada como postgres.',
            current_user;
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_catalog.pg_proc AS p
          JOIN pg_catalog.pg_namespace AS n
            ON n.oid = p.pronamespace
         WHERE p.oid = pg_catalog.to_regprocedure(
                   'public.sp_realizar_locacao(integer,integer,integer[],integer,integer)'
               )
           AND n.nspname = 'public'
           AND p.prokind = 'p'
           AND p.prosecdef IS TRUE
           AND pg_catalog.pg_get_userbyid(p.proowner) = 'postgres'
    ) THEN
        RAISE EXCEPTION
            'ABORTADO: procedure esperada nao encontrada, nao e SECURITY DEFINER ou nao pertence a postgres.';
    END IF;
END;
$precheck$;

CREATE OR REPLACE PROCEDURE public.sp_realizar_locacao(
    IN p_id_cliente integer,
    IN p_id_funcionario integer,
    IN p_midias integer[],
    IN p_dias integer,
    INOUT p_id_locacao integer
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp
AS $procedure$
DECLARE
    v_ativo boolean;
    v_qtd   integer;
    v_soma  numeric(10,2);
BEGIN
    IF p_dias IS NULL OR p_dias <= 0 THEN
        RAISE EXCEPTION 'Quantidade de dias invalida: %', p_dias;
    END IF;

    IF p_midias IS NULL OR cardinality(p_midias) = 0 THEN
        RAISE EXCEPTION 'Informe ao menos uma midia';
    END IF;

    SELECT c.ativo
      INTO v_ativo
      FROM public.cliente AS c
     WHERE c.id_cliente = p_id_cliente;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Cliente % nao encontrado', p_id_cliente;
    END IF;

    IF NOT v_ativo THEN
        RAISE EXCEPTION 'Cliente % esta inativo', p_id_cliente;
    END IF;

    SELECT f.ativo
      INTO v_ativo
      FROM public.funcionario AS f
     WHERE f.id_funcionario = p_id_funcionario;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Funcionario % nao encontrado', p_id_funcionario;
    END IF;

    IF NOT v_ativo THEN
        RAISE EXCEPTION 'Funcionario % esta inativo', p_id_funcionario;
    END IF;

    SELECT count(*), sum(m.valor_diaria)
      INTO v_qtd, v_soma
      FROM public.midia AS m
     WHERE m.id_midia = ANY(p_midias);

    IF v_qtd <> cardinality(p_midias) THEN
        RAISE EXCEPTION 'Lista de midias invalida (id inexistente ou repetido)';
    END IF;

    INSERT INTO public.locacao
        (id_cliente, id_funcionario, data_locacao,
         prazo_devolucao, status, valor_total)
    VALUES
        (p_id_cliente, p_id_funcionario, CURRENT_DATE,
         CURRENT_DATE + p_dias, 'ATIVA', v_soma * p_dias)
    RETURNING id_locacao INTO p_id_locacao;

    INSERT INTO public.item_locacao (id_locacao, id_midia, valor_diaria)
    SELECT p_id_locacao, m.id_midia, m.valor_diaria
      FROM public.midia AS m
     WHERE m.id_midia = ANY(p_midias);
END;
$procedure$;

DO $postcheck$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_catalog.pg_proc AS p
          JOIN pg_catalog.pg_namespace AS n
            ON n.oid = p.pronamespace
         WHERE p.oid = pg_catalog.to_regprocedure(
                   'public.sp_realizar_locacao(integer,integer,integer[],integer,integer)'
               )
           AND n.nspname = 'public'
           AND p.prokind = 'p'
           AND p.prosecdef IS TRUE
           AND pg_catalog.pg_get_userbyid(p.proowner) = 'postgres'
           AND p.proconfig @> ARRAY['search_path=pg_catalog, public, pg_temp']
    ) THEN
        RAISE EXCEPTION
            'FALHA NA VALIDACAO: owner, SECURITY DEFINER ou search_path diferente do esperado.';
    END IF;
END;
$postcheck$;

COMMIT;

-- Conferencia final: deve retornar 1 linha e search_path=pg_catalog, public, pg_temp.
SELECT
    current_database() AS banco_atual,
    pg_catalog.pg_get_userbyid(p.proowner) AS proprietario,
    p.prosecdef AS security_definer,
    p.proconfig AS configuracoes,
    p.proacl AS privilegios
FROM pg_catalog.pg_proc AS p
WHERE p.oid = pg_catalog.to_regprocedure(
    'public.sp_realizar_locacao(integer,integer,integer[],integer,integer)'
);
