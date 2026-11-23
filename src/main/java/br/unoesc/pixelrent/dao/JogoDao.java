package br.unoesc.pixelrent.dao;

import br.unoesc.pixelrent.model.Jogo;
import br.unoesc.pixelrent.model.RegistroCatalogo;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Set;

/** Acesso JDBC ao catálogo de jogos e às plataformas associadas. */
public final class JogoDao {

    private static final String SELECT_BASE = """
            SELECT j.id_jogo, j.titulo, j.ano_lancamento,
                   j.classificacao_indicativa, j.id_genero, g.nome AS nome_genero,
                   COALESCE(
                       array_agg(DISTINCT jp.id_plataforma)
                           FILTER (WHERE jp.id_plataforma IS NOT NULL),
                       ARRAY[]::integer[]
                   ) AS ids_plataformas,
                   COALESCE(string_agg(DISTINCT p.nome, ', ' ORDER BY p.nome), '')
                       AS nomes_plataformas
              FROM public.jogo j
              JOIN public.genero g ON g.id_genero = j.id_genero
              LEFT JOIN public.jogo_plataforma jp ON jp.id_jogo = j.id_jogo
              LEFT JOIN public.plataforma p ON p.id_plataforma = jp.id_plataforma
            """;

    private static final String GROUP_ORDER = """
             GROUP BY j.id_jogo, j.titulo, j.ano_lancamento,
                      j.classificacao_indicativa, j.id_genero, g.nome
             ORDER BY j.titulo, j.id_jogo
            """;

    private static final String SQL_LISTAR = SELECT_BASE + """
             WHERE (? = FALSE
                    OR j.titulo ILIKE ?
                    OR g.nome ILIKE ?
                    OR EXISTS (
                        SELECT 1
                          FROM public.jogo_plataforma jp2
                          JOIN public.plataforma p2
                            ON p2.id_plataforma = jp2.id_plataforma
                         WHERE jp2.id_jogo = j.id_jogo
                           AND p2.nome ILIKE ?
                    ))
            """ + GROUP_ORDER;

    private static final String SQL_BUSCAR = SELECT_BASE + """
             WHERE j.id_jogo = ?
            """ + GROUP_ORDER;

    private static final String SQL_LISTAR_GENEROS = """
            SELECT id_genero, nome FROM public.genero ORDER BY nome, id_genero
            """;

    private static final String SQL_LISTAR_PLATAFORMAS = """
            SELECT id_plataforma, nome FROM public.plataforma ORDER BY nome, id_plataforma
            """;

    private static final String SQL_INSERIR = """
            INSERT INTO public.jogo
                (titulo, ano_lancamento, classificacao_indicativa, id_genero)
            VALUES (?, ?, ?, ?)
            RETURNING id_jogo
            """;

    private static final String SQL_ATUALIZAR = """
            UPDATE public.jogo
               SET titulo = ?, ano_lancamento = ?,
                   classificacao_indicativa = ?, id_genero = ?
             WHERE id_jogo = ?
            RETURNING id_jogo
            """;

    public List<Jogo> listar(Connection conexao, String pesquisa) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        String termo = pesquisa == null || pesquisa.isBlank() ? null : "%" + pesquisa.trim() + "%";
        try (PreparedStatement comando = conexao.prepareStatement(SQL_LISTAR)) {
            comando.setBoolean(1, termo != null);
            if (termo == null) {
                comando.setNull(2, Types.VARCHAR);
                comando.setNull(3, Types.VARCHAR);
                comando.setNull(4, Types.VARCHAR);
            } else {
                comando.setString(2, termo);
                comando.setString(3, termo);
                comando.setString(4, termo);
            }
            try (ResultSet resultado = comando.executeQuery()) {
                List<Jogo> jogos = new ArrayList<>();
                while (resultado.next()) jogos.add(mapear(resultado));
                return List.copyOf(jogos);
            }
        }
    }

    public Optional<Jogo> buscarPorId(Connection conexao, int id) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        try (PreparedStatement comando = conexao.prepareStatement(SQL_BUSCAR)) {
            comando.setInt(1, id);
            try (ResultSet resultado = comando.executeQuery()) {
                return resultado.next() ? Optional.of(mapear(resultado)) : Optional.empty();
            }
        }
    }

    public List<RegistroCatalogo> listarGeneros(Connection conexao) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        try (PreparedStatement comando = conexao.prepareStatement(SQL_LISTAR_GENEROS);
             ResultSet resultado = comando.executeQuery()) {
            List<RegistroCatalogo> itens = new ArrayList<>();
            while (resultado.next()) {
                itens.add(new RegistroCatalogo(
                        resultado.getInt("id_genero"), resultado.getString("nome")));
            }
            return List.copyOf(itens);
        }
    }

    public List<RegistroCatalogo> listarPlataformas(Connection conexao) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        try (PreparedStatement comando = conexao.prepareStatement(SQL_LISTAR_PLATAFORMAS);
             ResultSet resultado = comando.executeQuery()) {
            List<RegistroCatalogo> itens = new ArrayList<>();
            while (resultado.next()) {
                itens.add(new RegistroCatalogo(
                        resultado.getInt("id_plataforma"), resultado.getString("nome")));
            }
            return List.copyOf(itens);
        }
    }

    public int inserir(Connection conexao, String titulo, int ano,
                       String classificacao, int idGenero) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        try (PreparedStatement comando = conexao.prepareStatement(SQL_INSERIR)) {
            comando.setString(1, titulo);
            comando.setShort(2, (short) ano);
            comando.setString(3, classificacao);
            comando.setInt(4, idGenero);
            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) throw new SQLException("O banco não retornou o jogo cadastrado.");
                return resultado.getInt("id_jogo");
            }
        }
    }

    public boolean atualizar(Connection conexao, int id, String titulo, int ano,
                             String classificacao, int idGenero) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        try (PreparedStatement comando = conexao.prepareStatement(SQL_ATUALIZAR)) {
            comando.setString(1, titulo);
            comando.setShort(2, (short) ano);
            comando.setString(3, classificacao);
            comando.setInt(4, idGenero);
            comando.setInt(5, id);
            try (ResultSet resultado = comando.executeQuery()) {
                return resultado.next();
            }
        }
    }

    /** Adiciona associações novas sem exigir DELETE, para respeitar o perfil atendente. */
    public void adicionarPlataformas(Connection conexao, int idJogo,
                                     List<Integer> idsPlataformas) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        try (PreparedStatement inserir = conexao.prepareStatement(
                "INSERT INTO public.jogo_plataforma (id_jogo, id_plataforma) VALUES (?, ?)")) {
            for (Integer idPlataforma : idsPlataformas) {
                inserir.setInt(1, idJogo);
                inserir.setInt(2, idPlataforma);
                inserir.addBatch();
            }
            inserir.executeBatch();
        }
    }

    public void substituirPlataformas(Connection conexao, int idJogo,
                                      List<Integer> idsPlataformas) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        Set<Integer> atuais = new LinkedHashSet<>();
        try (PreparedStatement consulta = conexao.prepareStatement(
                "SELECT id_plataforma FROM public.jogo_plataforma WHERE id_jogo = ?")) {
            consulta.setInt(1, idJogo);
            try (ResultSet resultado = consulta.executeQuery()) {
                while (resultado.next()) atuais.add(resultado.getInt(1));
            }
        }
        Set<Integer> desejadas = new LinkedHashSet<>(idsPlataformas);
        // Remove somente associações realmente desmarcadas. Isso evita tentar
        // apagar/recriar vínculos que já são referenciados por mídias.
        try (PreparedStatement excluir = conexao.prepareStatement(
                "DELETE FROM public.jogo_plataforma WHERE id_jogo = ? AND id_plataforma = ?")) {
            for (Integer idAtual : atuais) {
                if (!desejadas.contains(idAtual)) {
                    excluir.setInt(1, idJogo);
                    excluir.setInt(2, idAtual);
                    excluir.addBatch();
                }
            }
            excluir.executeBatch();
        }
        try (PreparedStatement inserir = conexao.prepareStatement(
                "INSERT INTO public.jogo_plataforma (id_jogo, id_plataforma) VALUES (?, ?)")) {
            for (Integer idNovo : desejadas) {
                if (!atuais.contains(idNovo)) {
                    inserir.setInt(1, idJogo);
                    inserir.setInt(2, idNovo);
                    inserir.addBatch();
                }
            }
            inserir.executeBatch();
        }
    }

    /** Remove as associações antes do jogo; a FK de mídias impede apagar jogos em uso. */
    public boolean excluir(Connection conexao, int id) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        try (PreparedStatement excluirAssociacoes = conexao.prepareStatement(
                "DELETE FROM public.jogo_plataforma WHERE id_jogo = ?")) {
            excluirAssociacoes.setInt(1, id);
            excluirAssociacoes.executeUpdate();
        }
        try (PreparedStatement excluirJogo = conexao.prepareStatement(
                "DELETE FROM public.jogo WHERE id_jogo = ?")) {
            excluirJogo.setInt(1, id);
            return excluirJogo.executeUpdate() > 0;
        }
    }

    private static Jogo mapear(ResultSet resultado) throws SQLException {
        Array array = resultado.getArray("ids_plataformas");
        List<Integer> ids = new ArrayList<>();
        if (array != null) {
            Object[] valores = (Object[]) array.getArray();
            for (Object valor : valores) ids.add(((Number) valor).intValue());
            array.free();
        }
        return new Jogo(
                resultado.getInt("id_jogo"),
                resultado.getString("titulo"),
                resultado.getInt("ano_lancamento"),
                resultado.getString("classificacao_indicativa"),
                resultado.getInt("id_genero"),
                resultado.getString("nome_genero"),
                ids,
                resultado.getString("nomes_plataformas"));
    }
}
