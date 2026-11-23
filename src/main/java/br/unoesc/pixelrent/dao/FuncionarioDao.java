package br.unoesc.pixelrent.dao;

import br.unoesc.pixelrent.model.Funcionario;
import br.unoesc.pixelrent.security.PerfilFuncionario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Acesso administrativo aos funcionários. A coluna senha_hash nunca é retornada. */
public final class FuncionarioDao {

    private static final String SQL_LISTAR = """
            SELECT id_funcionario, nome, BTRIM(cpf) AS cpf, telefone, email,
                   login, perfil, ativo
              FROM public.funcionario
             WHERE (? = TRUE OR ativo = TRUE)
               AND (? = FALSE OR nome ILIKE ? OR login ILIKE ? OR BTRIM(cpf) LIKE ?)
             ORDER BY ativo DESC, nome, id_funcionario
            """;

    private static final String SQL_BUSCAR = """
            SELECT id_funcionario, nome, BTRIM(cpf) AS cpf, telefone, email,
                   login, perfil, ativo
              FROM public.funcionario
             WHERE id_funcionario = ?
            """;

    private static final String SQL_INSERIR = """
            INSERT INTO public.funcionario
                (nome, cpf, telefone, email, login, senha_hash, perfil, ativo)
            VALUES (?, ?, ?, ?, ?, public.crypt(?, public.gen_salt('bf', 12)), ?, TRUE)
            RETURNING id_funcionario, nome, BTRIM(cpf) AS cpf, telefone, email,
                      login, perfil, ativo
            """;

    private static final String SQL_ATUALIZAR = """
            UPDATE public.funcionario
               SET nome = ?, cpf = ?, telefone = ?, email = ?, login = ?, perfil = ?
             WHERE id_funcionario = ?
            RETURNING id_funcionario, nome, BTRIM(cpf) AS cpf, telefone, email,
                      login, perfil, ativo
            """;

    private static final String SQL_ATUALIZAR_COM_SENHA = """
            UPDATE public.funcionario
               SET nome = ?, cpf = ?, telefone = ?, email = ?, login = ?, perfil = ?,
                   senha_hash = public.crypt(?, public.gen_salt('bf', 12))
             WHERE id_funcionario = ?
            RETURNING id_funcionario, nome, BTRIM(cpf) AS cpf, telefone, email,
                      login, perfil, ativo
            """;

    private static final String SQL_ALTERAR_ATIVO = """
            UPDATE public.funcionario SET ativo = ? WHERE id_funcionario = ?
            """;

    private static final String SQL_BLOQUEAR_ADMINS_ATIVOS = """
            SELECT id_funcionario FROM public.funcionario
             WHERE ativo = TRUE AND perfil = 'ADMINISTRADOR'
             ORDER BY id_funcionario
             FOR UPDATE
            """;

    public List<Funcionario> listar(Connection conexao, boolean incluirInativos, String pesquisa)
            throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        String termo = pesquisa == null ? null : pesquisa.trim();
        boolean pesquisar = termo != null && !termo.isEmpty();
        String nomeLogin = pesquisar ? "%" + termo + "%" : null;
        String cpf = pesquisar ? "%" + (termo.replaceAll("\\D", "").isEmpty() ? "__SEM_CPF__" : termo.replaceAll("\\D", "")) + "%" : null;

        try (PreparedStatement comando = conexao.prepareStatement(SQL_LISTAR)) {
            comando.setBoolean(1, incluirInativos);
            comando.setBoolean(2, pesquisar);
            if (pesquisar) {
                comando.setString(3, nomeLogin);
                comando.setString(4, nomeLogin);
                comando.setString(5, cpf);
            } else {
                comando.setNull(3, Types.VARCHAR);
                comando.setNull(4, Types.VARCHAR);
                comando.setNull(5, Types.VARCHAR);
            }
            try (ResultSet resultado = comando.executeQuery()) {
                List<Funcionario> itens = new ArrayList<>();
                while (resultado.next()) itens.add(mapear(resultado));
                return List.copyOf(itens);
            }
        }
    }

    public Optional<Funcionario> buscarPorId(Connection conexao, int idFuncionario) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        try (PreparedStatement comando = conexao.prepareStatement(SQL_BUSCAR)) {
            comando.setInt(1, idFuncionario);
            try (ResultSet resultado = comando.executeQuery()) {
                return resultado.next() ? Optional.of(mapear(resultado)) : Optional.empty();
            }
        }
    }

    public Funcionario inserir(Connection conexao, String nome, String cpf, String telefone,
            String email, String login, String senha, PerfilFuncionario perfil) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        try (PreparedStatement comando = conexao.prepareStatement(SQL_INSERIR)) {
            comando.setString(1, nome);
            comando.setString(2, cpf);
            definirOpcional(comando, 3, telefone);
            definirOpcional(comando, 4, email);
            comando.setString(5, login);
            comando.setString(6, senha);
            comando.setString(7, perfil.name());
            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) throw new SQLException("O banco não retornou o funcionário cadastrado.");
                return mapear(resultado);
            }
        }
    }

    public Optional<Funcionario> atualizar(Connection conexao, int idFuncionario, String nome,
            String cpf, String telefone, String email, String login, String senha,
            PerfilFuncionario perfil) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        boolean alterarSenha = senha != null && !senha.isEmpty();
        String sql = alterarSenha ? SQL_ATUALIZAR_COM_SENHA : SQL_ATUALIZAR;
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setString(1, nome);
            comando.setString(2, cpf);
            definirOpcional(comando, 3, telefone);
            definirOpcional(comando, 4, email);
            comando.setString(5, login);
            comando.setString(6, perfil.name());
            if (alterarSenha) {
                comando.setString(7, senha);
                comando.setInt(8, idFuncionario);
            } else {
                comando.setInt(7, idFuncionario);
            }
            try (ResultSet resultado = comando.executeQuery()) {
                return resultado.next() ? Optional.of(mapear(resultado)) : Optional.empty();
            }
        }
    }

    public boolean alterarAtivo(Connection conexao, int idFuncionario, boolean ativo) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        try (PreparedStatement comando = conexao.prepareStatement(SQL_ALTERAR_ATIVO)) {
            comando.setBoolean(1, ativo);
            comando.setInt(2, idFuncionario);
            return comando.executeUpdate() > 0;
        }
    }

    /** Bloqueia os administradores ativos em ordem estável para proteger o último administrador.
     * Deve ser chamado dentro de uma transação com autoCommit=false.
     */
    public long bloquearEContarAdministradoresAtivos(Connection conexao) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        if (conexao.getAutoCommit()) {
            throw new SQLException("A proteção do último administrador exige uma transação.");
        }
        try (PreparedStatement comando = conexao.prepareStatement(SQL_BLOQUEAR_ADMINS_ATIVOS);
             ResultSet resultado = comando.executeQuery()) {
            long quantidade = 0;
            while (resultado.next()) quantidade++;
            return quantidade;
        }
    }

    private static void definirOpcional(PreparedStatement comando, int indice, String valor)
            throws SQLException {
        if (valor == null || valor.isBlank()) comando.setNull(indice, Types.VARCHAR);
        else comando.setString(indice, valor);
    }

    private static Funcionario mapear(ResultSet resultado) throws SQLException {
        PerfilFuncionario perfil;
        try {
            perfil = PerfilFuncionario.deBanco(resultado.getString("perfil"));
        } catch (IllegalArgumentException e) {
            throw new SQLException("O banco retornou um perfil de funcionário inválido.", e);
        }
        return new Funcionario(
                resultado.getInt("id_funcionario"), resultado.getString("nome"),
                resultado.getString("cpf").trim(), resultado.getString("telefone"),
                resultado.getString("email"), resultado.getString("login"), perfil,
                resultado.getBoolean("ativo"));
    }
}
