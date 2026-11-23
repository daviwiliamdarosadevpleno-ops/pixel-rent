
package br.unoesc.pixelrent.service;

import br.unoesc.pixelrent.dao.ClienteDao;
import br.unoesc.pixelrent.db.ConnectionProvider;
import br.unoesc.pixelrent.exception.ConfiguracaoException;
import br.unoesc.pixelrent.exception.DadosInvalidosException;
import br.unoesc.pixelrent.exception.FalhaTecnicaException;
import br.unoesc.pixelrent.model.Cliente;
import br.unoesc.pixelrent.security.Permissao;
import br.unoesc.pixelrent.security.PoliticaAcesso;
import br.unoesc.pixelrent.security.Sessao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class ClienteService {

    private final ConnectionProvider connectionProvider;
    private final ClienteDao clienteDao;
    private final PoliticaAcesso politicaAcesso;

    public ClienteService(
            ConnectionProvider connectionProvider,
            ClienteDao clienteDao,
            PoliticaAcesso politicaAcesso) {

        this.connectionProvider = Objects.requireNonNull(
                connectionProvider, "connectionProvider");

        this.clienteDao = Objects.requireNonNull(
                clienteDao, "clienteDao");

        this.politicaAcesso = Objects.requireNonNull(
                politicaAcesso, "politicaAcesso");
    }

    // LISTAGEM

    public List<Cliente> listar(
            Sessao sessao, boolean incluirInativos) {

        return listar(sessao, incluirInativos, null);
    }

    public List<Cliente> listar(
            Sessao sessao,
            boolean incluirInativos,
            String pesquisa) {

        exigirPermissao(sessao);

        String termo = pesquisa == null || pesquisa.isBlank()
                ? null : pesquisa.trim();

        try (Connection conexao =
                     connectionProvider.abrirSomenteLeitura(
                             sessao.perfilConexao())) {

            return clienteDao.listar(
                    conexao, incluirInativos, termo);

        } catch (SQLException e) {
            throw traduzirErro("consultar clientes", e);

        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException(
                    "Não foi possível configurar o acesso aos clientes.", e);
        }
    }

    // BUSCA POR IDENTIFICADOR

    public Optional<Cliente> buscarPorId(
            Sessao sessao, int idCliente) {

        exigirPermissao(sessao);
        validarId(idCliente);

        try (Connection conexao =
                     connectionProvider.abrirSomenteLeitura(
                             sessao.perfilConexao())) {

            return clienteDao.buscarPorId(conexao, idCliente);

        } catch (SQLException e) {
            throw traduzirErro("consultar o cliente", e);

        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException(
                    "Não foi possível configurar o acesso aos clientes.", e);
        }
    }

    // CADASTRO

    public Cliente cadastrar(
            Sessao sessao,
            String nome,
            String cpf,
            String telefone,
            String email) {

        exigirPermissao(sessao);

        String nomeValidado = validarNome(nome);
        String cpfValidado = validarCpf(cpf);
        String telefoneValidado = validarOpcional(
                telefone, 20, "telefone");
        String emailValidado = validarEmail(email);

        try (Connection conexao =
                     connectionProvider.abrir(sessao.perfilConexao())) {

            return clienteDao.inserir(
                    conexao,
                    nomeValidado,
                    cpfValidado,
                    telefoneValidado,
                    emailValidado);

        } catch (SQLException e) {
            throw traduzirErro("cadastrar o cliente", e);

        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException(
                    "Não foi possível configurar o acesso aos clientes.", e);
        }
    }

    // ATUALIZAÇÃO

    public Optional<Cliente> atualizar(
            Sessao sessao,
            int idCliente,
            String nome,
            String cpf,
            String telefone,
            String email) {

        exigirPermissao(sessao);
        validarId(idCliente);

        String nomeValidado = validarNome(nome);
        String cpfValidado = validarCpf(cpf);
        String telefoneValidado = validarOpcional(
                telefone, 20, "telefone");
        String emailValidado = validarEmail(email);

        try (Connection conexao =
                     connectionProvider.abrir(sessao.perfilConexao())) {

            return clienteDao.atualizar(
                    conexao,
                    idCliente,
                    nomeValidado,
                    cpfValidado,
                    telefoneValidado,
                    emailValidado);

        } catch (SQLException e) {
            throw traduzirErro("atualizar o cliente", e);

        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException(
                    "Não foi possível configurar o acesso aos clientes.", e);
        }
    }

    // ATIVAÇÃO E DESATIVAÇÃO

    public boolean alterarAtivo(
            Sessao sessao,
            int idCliente,
            boolean ativo) {

        exigirPermissao(sessao);
        validarId(idCliente);

        try (Connection conexao =
                     connectionProvider.abrir(sessao.perfilConexao())) {

            return clienteDao.alterarAtivo(
                    conexao, idCliente, ativo);

        } catch (SQLException e) {
            throw traduzirErro("alterar a situação do cliente", e);

        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException(
                    "Não foi possível configurar o acesso aos clientes.", e);
        }
    }

    // PERMISSÕES

    private void exigirPermissao(Sessao sessao) {
        politicaAcesso.exigir(sessao, Permissao.CLIENTES);
    }

    // VALIDAÇÕES

    private static void validarId(int idCliente) {
        if (idCliente <= 0) {
            throw new DadosInvalidosException(
                    "Identificador de cliente inválido.");
        }
    }

    private static String validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new DadosInvalidosException(
                    "Informe o nome do cliente.");
        }

        String valor = nome.trim();

        if (valor.length() > 100) {
            throw new DadosInvalidosException(
                    "O nome deve possuir no máximo 100 caracteres.");
        }

        return valor;
    }

    private static String validarCpf(String cpf) {
        if (cpf == null || cpf.isBlank()) {
            throw new DadosInvalidosException(
                    "Informe o CPF do cliente.");
        }

        String valor = cpf.replaceAll("\\D", "");

        if (valor.length() != 11
                || valor.chars().distinct().count() == 1) {

            throw new DadosInvalidosException(
                    "Informe um CPF válido.");
        }

        int primeiro = calcularDigitoCpf(
                valor.substring(0, 9), 10);

        int segundo = calcularDigitoCpf(
                valor.substring(0, 9) + primeiro, 11);

        if (valor.charAt(9) - '0' != primeiro
                || valor.charAt(10) - '0' != segundo) {

            throw new DadosInvalidosException(
                    "Informe um CPF válido.");
        }

        return valor;
    }

    private static int calcularDigitoCpf(
            String base, int pesoInicial) {

        int soma = 0;

        for (int i = 0; i < base.length(); i++) {
            soma += (base.charAt(i) - '0')
                    * (pesoInicial - i);
        }

        int resto = soma % 11;

        return resto < 2 ? 0 : 11 - resto;
    }

    private static String validarEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }

        String valor = email.trim();

        if (valor.length() > 100
                || !valor.matches(
                        "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {

            throw new DadosInvalidosException(
                    "Informe um endereço de e-mail válido.");
        }

        return valor;
    }

    private static String validarOpcional(
            String valor, int limite, String campo) {

        if (valor == null || valor.isBlank()) {
            return null;
        }

        String resultado = valor.trim();

        if (resultado.length() > limite) {
            throw new DadosInvalidosException(
                    "O campo " + campo
                            + " deve possuir no máximo "
                            + limite + " caracteres.");
        }

        return resultado;
    }

    // TRATAMENTO DE ERROS DO BANCO

    private static RuntimeException traduzirErro(
            String operacao, SQLException erro) {

        if ("23505".equals(erro.getSQLState())) {

            String detalhe = erro.getMessage() == null
                    ? ""
                    : erro.getMessage().toLowerCase(Locale.ROOT);

            if (detalhe.contains("cliente_cpf_key")) {
                return new DadosInvalidosException(
                        "Já existe um cliente cadastrado com este CPF.");
            }

            if (detalhe.contains("cliente_email_key")) {
                return new DadosInvalidosException(
                        "Já existe um cliente cadastrado com este e-mail.");
            }

            return new DadosInvalidosException(
                    "Já existe um cadastro com os dados informados.");
        }

        return new FalhaTecnicaException(
                "Não foi possível " + operacao + ".", erro);
    }
}
