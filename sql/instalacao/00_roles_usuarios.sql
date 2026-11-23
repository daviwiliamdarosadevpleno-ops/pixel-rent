
-- ============================================================
-- PIXEL RENT - 00 - CRIACAO DE GRUPOS E USUARIOS
-- Executar antes de restaurar a estrutura do banco.
--
-- Executar conectado como administrador do PostgreSQL.
-- Nenhuma senha e armazenada neste arquivo.
-- ============================================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_roles WHERE rolname = 'grp_admin'
    ) THEN
        EXECUTE 'CREATE ROLE grp_admin NOLOGIN';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_roles WHERE rolname = 'grp_atendente'
    ) THEN
        EXECUTE 'CREATE ROLE grp_atendente NOLOGIN';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_roles WHERE rolname = 'usr_admin_teste'
    ) THEN
        EXECUTE 'CREATE ROLE usr_admin_teste LOGIN INHERIT';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_roles WHERE rolname = 'usr_atendente_teste'
    ) THEN
        EXECUTE 'CREATE ROLE usr_atendente_teste LOGIN INHERIT';
    END IF;
END
$$;

-- Garantir que os grupos nao possam autenticar diretamente.
ALTER ROLE grp_admin NOLOGIN;
ALTER ROLE grp_atendente NOLOGIN;

-- Garantir que as contas possam autenticar.
-- Senhas existentes nao sao modificadas.
ALTER ROLE usr_admin_teste LOGIN INHERIT;
ALTER ROLE usr_atendente_teste LOGIN INHERIT;

-- Associar cada conta ao grupo correspondente.
GRANT grp_admin TO usr_admin_teste;
GRANT grp_atendente TO usr_atendente_teste;

COMMENT ON ROLE grp_admin IS
    'Grupo de permissoes administrativas do Pixel Rent';

COMMENT ON ROLE grp_atendente IS
    'Grupo de permissoes de atendente do Pixel Rent';
