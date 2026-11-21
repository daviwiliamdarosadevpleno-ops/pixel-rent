package br.unoesc.pixelrent.dao;

import br.unoesc.pixelrent.security.PerfilFuncionario;
import br.unoesc.pixelrent.security.Sessao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

/** Acesso JDBC à função de autenticação já existente no PostgreSQL. */
public final class AutenticacaoDao {

    private static final String SQL_AUTENTICAR = """
            SELECT id_funcionario, nome, perfil
              FROM public.fn_autenticar_funcionario(?, ?)
            """;

    /**
     * Consulta a função de autenticação. Não lê nem recebe a coluna {@code senha_hash}.
     * O chamador é responsável por fornecer e apagar o array da senha.
     *
     * @param conexao conexão já aberta pelo service
     * @param login login informado na tela
     * @param senha senha em memória, usada apenas para vincular o parâmetro JDBC
     * @return sessão quando a função valida um funcionário ativo; vazio caso contrário
     * @throws SQLException se houver falha JDBC ou perfil inesperado retornado pelo banco
     */
    public Optional<Sessao> autenticar(Connection conexao, String login, char[] senha)
            throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        Objects.requireNonNull(login, "login");
        Objects.requireNonNull(senha, "senha");

        try (PreparedStatement comando = conexao.prepareStatement(SQL_AUTENTICAR)) {
            comando.setString(1, login);
            // O pgjdbc recebe texto para o parâmetro PostgreSQL TEXT. O array original é
            // apagado pelo AutenticacaoService assim que a chamada termina.
            comando.setString(2, new String(senha));

            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) {
                    return Optional.empty();
                }

                int idFuncionario = resultado.getInt("id_funcionario");
                if (resultado.wasNull()) {
                    throw new SQLException("A função de autenticação retornou um identificador nulo.");
                }
                String nome = resultado.getString("nome");
                String perfilBanco = resultado.getString("perfil");

                if (resultado.next()) {
                    throw new SQLException("A função de autenticação retornou mais de um funcionário.");
                }

                final PerfilFuncionario perfil;
                try {
                    perfil = PerfilFuncionario.deBanco(perfilBanco);
                } catch (IllegalArgumentException e) {
                    throw new SQLException("A função de autenticação retornou um perfil inválido.", e);
                }
                try {
                    return Optional.of(new Sessao(idFuncionario, nome, perfil));
                } catch (IllegalArgumentException | NullPointerException e) {
                    throw new SQLException("A função de autenticação retornou dados incompletos.", e);
                }
            }
        }
    }
}
