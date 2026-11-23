
-- ============================================================
-- PIXEL RENT - 04 - DADOS INICIAIS
--
-- Cadastra os generos e plataformas basicos do sistema.
-- Os identificadores sao gerados pelas sequences.
-- A verificacao por nome evita duplicacoes em reexecucoes.
-- ============================================================

BEGIN;

-- Generos
INSERT INTO public.genero (nome)
SELECT dados.nome
FROM (
    VALUES
        ('Ação'),
        ('Aventura'),
        ('Esporte'),
        ('RPG'),
        ('Corrida')
) AS dados(nome)
WHERE NOT EXISTS (
    SELECT 1
    FROM public.genero g
    WHERE LOWER(BTRIM(g.nome)) = LOWER(BTRIM(dados.nome))
);

-- Plataformas
INSERT INTO public.plataforma (nome)
SELECT dados.nome
FROM (
    VALUES
        ('PlayStation 5'),
        ('Xbox Series X'),
        ('Nintendo Switch'),
        ('PC')
) AS dados(nome)
WHERE NOT EXISTS (
    SELECT 1
    FROM public.plataforma p
    WHERE LOWER(BTRIM(p.nome)) = LOWER(BTRIM(dados.nome))
);

COMMIT;
