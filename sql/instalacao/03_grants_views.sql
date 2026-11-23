
-- ============================================================
-- PIXEL RENT - 03 - PERMISSOES DAS VIEWS
-- Executar depois de restaurar a estrutura do banco.
--
-- Reproduz a politica de acesso validada em pixelrent_web_test.
--
-- ADMINISTRADOR:
--   SELECT nas seis views.
--
-- ATENDENTE:
--   SELECT somente em quatro views.
--   Sem acesso direto ao catalogo disponivel e faturamento.
-- ============================================================

BEGIN;

-- Remove concessoes anteriores para evitar permissoes
-- indevidas herdadas do dump original.
REVOKE ALL PRIVILEGES ON TABLE
    public.vw_catalogo_disponivel,
    public.vw_faturamento_mensal,
    public.vw_historico_cliente,
    public.vw_jogos_mais_alugados,
    public.vw_locacoes_ativas,
    public.vw_locacoes_atrasadas
FROM grp_admin, grp_atendente;

-- Ambos os grupos precisam acessar o schema.
GRANT USAGE ON SCHEMA public
TO grp_admin, grp_atendente;

-- Administrador: somente leitura nas seis views.
GRANT SELECT ON TABLE
    public.vw_catalogo_disponivel,
    public.vw_faturamento_mensal,
    public.vw_historico_cliente,
    public.vw_jogos_mais_alugados,
    public.vw_locacoes_ativas,
    public.vw_locacoes_atrasadas
TO grp_admin;

-- Atendente: somente nas quatro views operacionais.
GRANT SELECT ON TABLE
    public.vw_historico_cliente,
    public.vw_jogos_mais_alugados,
    public.vw_locacoes_ativas,
    public.vw_locacoes_atrasadas
TO grp_atendente;

COMMIT;
