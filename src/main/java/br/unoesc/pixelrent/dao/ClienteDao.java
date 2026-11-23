
package br.unoesc.pixelrent.dao;

import br.unoesc.pixelrent.model.Cliente;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Acesso JDBC aos dados dos clientes.
 */
public final class ClienteDao {

    private static final String COLUNAS = """
            id_cliente, nome, cpf, telefone, email, ativo, data_cadastro
            """;

    private static final String SQL_LISTAR = """
            SELECT id_cliente, nome, cpf, telefone, email, ativo, data_cadastro
              FROM public.cliente
             WHERE (? = TRUE OR ativo = TRUE)
               AND (? = FALSE OR nome ILIKE ? OR BTRIM(cpf) LIKE ?)
             ORDER BY nome
            """;

    private static final String SQL_BUSCAR = """
            SELECT id_cliente, nome, cpf, telefone, email, ativo, data_cadastro
              FROM public.cliente
             WHERE id_cliente = ?
            """;

    private static final String SQL_INSERIR = """
            INSERT INTO public.cliente (nome, cpf, telefone, email)
            VALUES (?, ?, ?, ?)
            RETURNING
            """ + COLUNAS;

    private static final String SQL_ATUALIZAR = """
            UPDATE public.cliente
               SET nome = ?, cpf = ?, telefone = ?, email = ?
             WHERE id_cliente = ?
            RETURNING
            """ + COLUNAS;

    private static final String SQL_ALTERAR_ATIVO = """
            UPDATE public.cliente
               SET ativo = ?
             WHERE id_cliente = ?
            """;

    /**
     * Lista clientes sem aplicar pesquisa.
     * Mantido para compatibilidade com o serviço existente.
     */
    public List<Cliente> listar(
            Connection conexao, boolean incluirInativos)
            throws SQLException {

        return listar(conexao, incluirInativos, null);
    }

    /**
     * Lista clientes com filtro de situação e pesquisa por nome ou CPF.
     */
    public List<Cliente> listar(
            Connection conexao,
            boolean incluirInativos,
            String pesquisa) throws SQLException {

        Objects.requireNonNull(conexao, "conexao");

        String termo = pesquisa == null ? null : pesquisa.trim();
        boolean pesquisar = termo != null && !termo.isEmpty();

        String numerosCpf = pesquisar
                ? termo.replaceAll("\\D", "")
                : "";

        String padraoNome = pesquisar
                ? "%" + termo + "%"
                : null;

        String padraoCpf = pesquisar
                ? "%" + (numerosCpf.isEmpty() ? termo : numerosCpf) + "%"
                : null;

        try (PreparedStatement comando =
                     conexao.prepareStatement(SQL_LISTAR)) {

            comando.setBoolean(1, incluirInativos);
            comando.setBoolean(2, pesquisar);

            if (pesquisar) {
                comando.setString(3, padraoNome);
                comando.setString(4, padraoCpf);
            } else {
                comando.setNull(3, Types.VARCHAR);
                comando.setNull(4, Types.VARCHAR);
            }

            try (ResultSet resultado = comando.executeQuery()) {
                List<Cliente> clientes = new ArrayList<>();

                while (resultado.next()) {
                    clientes.add(mapear(resultado));
                }

                return List.copyOf(clientes);
            }
        }
    }

    /**
     * Busca um cliente pelo identificador.
     */
    public Optional<Cliente> buscarPorId(
            Connection conexao, int idCliente) throws SQLException {

        Objects.requireNonNull(conexao, "conexao");

        try (PreparedStatement comando =
                     conexao.prepareStatement(SQL_BUSCAR)) {

            comando.setInt(1, idCliente);

            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapear(resultado));
            }
        }
    }

    /**
     * Insere um cliente e retorna os dados gerados pelo banco.
     */
    public Cliente inserir(
            Connection conexao,
            String nome,
            String cpf,
            String telefone,
            String email) throws SQLException {

        Objects.requireNonNull(conexao, "conexao");

        try (PreparedStatement comando =
                     conexao.prepareStatement(SQL_INSERIR)) {

            comando.setString(1, nome.trim());
            comando.setString(2, cpf.trim());
            definirOpcional(comando, 3, telefone);
            definirOpcional(comando, 4, email);

            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) {
                    throw new SQLException(
                            "O banco não retornou o cliente cadastrado.");
                }

                return mapear(resultado);
            }
        }
    }

    /**
     * Atualiza os dados cadastrais de um cliente.
     */
    public Optional<Cliente> atualizar(
            Connection conexao,
            int idCliente,
            String nome,
            String cpf,
            String telefone,
            String email) throws SQLException {

        Objects.requireNonNull(conexao, "conexao");

        try (PreparedStatement comando =
                     conexao.prepareStatement(SQL_ATUALIZAR)) {

            comando.setString(1, nome.trim());
            comando.setString(2, cpf.trim());
            definirOpcional(comando, 3, telefone);
            definirOpcional(comando, 4, email);
            comando.setInt(5, idCliente);

            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapear(resultado));
            }
        }
    }

    /**
     * Altera a situação do cliente sem remover seu histórico.
     */
    public boolean alterarAtivo(
            Connection conexao,
            int idCliente,
            boolean ativo) throws SQLException {

        Objects.requireNonNull(conexao, "conexao");

        try (PreparedStatement comando =
                     conexao.prepareStatement(SQL_ALTERAR_ATIVO)) {

            comando.setBoolean(1, ativo);
            comando.setInt(2, idCliente);

            return comando.executeUpdate() > 0;
        }
    }

    private static void definirOpcional(
            PreparedStatement comando,
            int indice,
            String valor) throws SQLException {

        if (valor == null || valor.isBlank()) {
            comando.setNull(indice, Types.VARCHAR);
        } else {
            comando.setString(indice, valor.trim());
        }
    }

    private static Cliente mapear(ResultSet resultado)
            throws SQLException {

        java.sql.Date dataCadastro =
                resultado.getDate("data_cadastro");

        if (dataCadastro == null) {
            throw new SQLException(
                    "O banco retornou um cliente sem data de cadastro.");
        }

        return new Cliente(
                resultado.getInt("id_cliente"),
                resultado.getString("nome"),
                resultado.getString("cpf").trim(),
                resultado.getString("telefone"),
                resultado.getString("email"),
                resultado.getBoolean("ativo"),
                dataCadastro.toLocalDate());
    }
}
