package br.unoesc.pixelrent.dao;

import br.unoesc.pixelrent.model.RegistroCatalogo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Acesso JDBC à tabela public.plataforma. */
public final class PlataformaDao {

    private static final String SQL_LISTAR = """
            SELECT id_plataforma, nome
              FROM public.plataforma
             WHERE (? = TRUE OR nome ILIKE ?)
             ORDER BY nome, id_plataforma
            """;

    private static final String SQL_BUSCAR = """
            SELECT id_plataforma, nome
              FROM public.plataforma
             WHERE id_plataforma = ?
            """;

    private static final String SQL_INSERIR = """
            INSERT INTO public.plataforma (nome)
            VALUES (?)
            RETURNING id_plataforma, nome
            """;

    private static final String SQL_ATUALIZAR = """
            UPDATE public.plataforma
               SET nome = ?
             WHERE id_plataforma = ?
            RETURNING id_plataforma, nome
            """;

    private static final String SQL_EXCLUIR = "DELETE FROM public.plataforma WHERE id_plataforma = ?";

    public List<RegistroCatalogo> listar(Connection conexao, String pesquisa) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        String termo = pesquisa == null ? null : pesquisa.trim();
        boolean pesquisar = termo != null && !termo.isEmpty();

        try (PreparedStatement comando = conexao.prepareStatement(SQL_LISTAR)) {
            comando.setBoolean(1, !pesquisar);
            if (pesquisar) {
                comando.setString(2, "%" + termo + "%");
            } else {
                comando.setNull(2, Types.VARCHAR);
            }
            try (ResultSet resultado = comando.executeQuery()) {
                List<RegistroCatalogo> registros = new ArrayList<>();
                while (resultado.next()) {
                    registros.add(mapear(resultado));
                }
                return List.copyOf(registros);
            }
        }
    }

    public Optional<RegistroCatalogo> buscarPorId(Connection conexao, int id) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        try (PreparedStatement comando = conexao.prepareStatement(SQL_BUSCAR)) {
            comando.setInt(1, id);
            try (ResultSet resultado = comando.executeQuery()) {
                return resultado.next() ? Optional.of(mapear(resultado)) : Optional.empty();
            }
        }
    }

    public RegistroCatalogo inserir(Connection conexao, String nome) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        try (PreparedStatement comando = conexao.prepareStatement(SQL_INSERIR)) {
            comando.setString(1, nome.trim());
            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) {
                    throw new SQLException("O banco não retornou a plataforma cadastrada.");
                }
                return mapear(resultado);
            }
        }
    }

    public Optional<RegistroCatalogo> atualizar(Connection conexao, int id, String nome) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        try (PreparedStatement comando = conexao.prepareStatement(SQL_ATUALIZAR)) {
            comando.setString(1, nome.trim());
            comando.setInt(2, id);
            try (ResultSet resultado = comando.executeQuery()) {
                return resultado.next() ? Optional.of(mapear(resultado)) : Optional.empty();
            }
        }
    }

    public boolean excluir(Connection conexao, int id) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        try (PreparedStatement comando = conexao.prepareStatement(SQL_EXCLUIR)) {
            comando.setInt(1, id);
            return comando.executeUpdate() > 0;
        }
    }

    private static RegistroCatalogo mapear(ResultSet resultado) throws SQLException {
        return new RegistroCatalogo(
                resultado.getInt("id_plataforma"),
                resultado.getString("nome"));
    }
}
