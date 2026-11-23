package br.unoesc.pixelrent.service;

import br.unoesc.pixelrent.dao.FuncionarioDao;
import br.unoesc.pixelrent.db.ConnectionProvider;
import br.unoesc.pixelrent.exception.ConfiguracaoException;
import br.unoesc.pixelrent.exception.DadosInvalidosException;
import br.unoesc.pixelrent.exception.FalhaTecnicaException;
import br.unoesc.pixelrent.model.Funcionario;
import br.unoesc.pixelrent.security.Permissao;
import br.unoesc.pixelrent.security.PerfilFuncionario;
import br.unoesc.pixelrent.security.PoliticaAcesso;
import br.unoesc.pixelrent.security.Sessao;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Cadastro administrativo de funcionários, sem remoção física dos registros. */
public final class FuncionarioService {
    private final ConnectionProvider provider;
    private final FuncionarioDao dao;
    private final PoliticaAcesso politica;

    public FuncionarioService(ConnectionProvider provider, FuncionarioDao dao, PoliticaAcesso politica) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.dao = Objects.requireNonNull(dao, "dao");
        this.politica = Objects.requireNonNull(politica, "politica");
    }

    public List<Funcionario> listar(Sessao sessao, boolean incluirInativos, String pesquisa) {
        exigirPermissao(sessao);
        try (Connection conexao = provider.abrirSomenteLeitura(sessao.perfilConexao())) {
            return dao.listar(conexao, incluirInativos, pesquisa);
        } catch (SQLException | ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível consultar os funcionários.", e);
        }
    }

    public Optional<Funcionario> buscarPorId(Sessao sessao, int idFuncionario) {
        exigirPermissao(sessao);
        validarId(idFuncionario);
        try (Connection conexao = provider.abrirSomenteLeitura(sessao.perfilConexao())) {
            return dao.buscarPorId(conexao, idFuncionario);
        } catch (SQLException | ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível consultar o funcionário.", e);
        }
    }

    public Funcionario cadastrar(Sessao sessao, String nome, String cpf, String telefone,
            String email, String login, String senha, String confirmarSenha, PerfilFuncionario perfil) {
        exigirPermissao(sessao);
        String nomeOk = validarNome(nome);
        String cpfOk = validarCpf(cpf);
        String telefoneOk = validarOpcional(telefone, 20, "telefone");
        String emailOk = validarEmail(email);
        String loginOk = validarLogin(login);
        String senhaOk = validarSenha(senha, true);
        validarConfirmacao(senhaOk, confirmarSenha);
        PerfilFuncionario perfilOk = validarPerfil(perfil);

        try (Connection conexao = provider.abrir(sessao.perfilConexao())) {
            return dao.inserir(conexao, nomeOk, cpfOk, telefoneOk, emailOk,
                    loginOk, senhaOk, perfilOk);
        } catch (SQLException e) {
            throw traduzirErro("cadastrar o funcionário", e);
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso aos funcionários.", e);
        }
    }

    public Optional<Funcionario> atualizar(Sessao sessao, int idFuncionario, String nome,
            String cpf, String telefone, String email, String login,
            String senha, String confirmarSenha, PerfilFuncionario perfil) {
        exigirPermissao(sessao);
        validarId(idFuncionario);
        String nomeOk = validarNome(nome);
        String cpfOk = validarCpf(cpf);
        String telefoneOk = validarOpcional(telefone, 20, "telefone");
        String emailOk = validarEmail(email);
        String loginOk = validarLogin(login);
        String senhaOk = validarSenha(senha, false);
        if (senhaOk != null) validarConfirmacao(senhaOk, confirmarSenha);
        else if (confirmarSenha != null && !confirmarSenha.isEmpty()) {
            throw new DadosInvalidosException("Preencha a nova senha e sua confirmação.");
        }
        PerfilFuncionario perfilOk = validarPerfil(perfil);

        try (Connection conexao = provider.abrir(sessao.perfilConexao())) {
            return emTransacao(conexao, () -> {
                long administradoresAtivos = dao.bloquearEContarAdministradoresAtivos(conexao);
                Optional<Funcionario> atual = dao.buscarPorId(conexao, idFuncionario);
                if (atual.isEmpty()) return Optional.empty();
                Funcionario existente = atual.get();

                if (idFuncionario == sessao.idFuncionario() && perfilOk != PerfilFuncionario.ADMINISTRADOR) {
                    throw new DadosInvalidosException("Você não pode remover seu próprio perfil de administrador.");
                }
                if (existente.ativo() && existente.perfil() == PerfilFuncionario.ADMINISTRADOR
                        && perfilOk != PerfilFuncionario.ADMINISTRADOR && administradoresAtivos <= 1) {
                    throw new DadosInvalidosException("O sistema precisa manter ao menos um administrador ativo.");
                }

                return dao.atualizar(conexao, idFuncionario, nomeOk, cpfOk, telefoneOk,
                        emailOk, loginOk, senhaOk, perfilOk);
            });
        } catch (SQLException e) {
            throw traduzirErro("atualizar o funcionário", e);
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso aos funcionários.", e);
        }
    }

    public boolean alterarAtivo(Sessao sessao, int idFuncionario, boolean ativo) {
        exigirPermissao(sessao);
        validarId(idFuncionario);
        if (!ativo && idFuncionario == sessao.idFuncionario()) {
            throw new DadosInvalidosException("Você não pode desativar sua própria conta.");
        }
        try (Connection conexao = provider.abrir(sessao.perfilConexao())) {
            return emTransacao(conexao, () -> {
                long administradoresAtivos = dao.bloquearEContarAdministradoresAtivos(conexao);
                Optional<Funcionario> atual = dao.buscarPorId(conexao, idFuncionario);
                if (atual.isEmpty()) return false;
                Funcionario existente = atual.get();
                if (!ativo && existente.ativo() && existente.perfil() == PerfilFuncionario.ADMINISTRADOR
                        && administradoresAtivos <= 1) {
                    throw new DadosInvalidosException("O sistema precisa manter ao menos um administrador ativo.");
                }
                return dao.alterarAtivo(conexao, idFuncionario, ativo);
            });
        } catch (SQLException e) {
            throw traduzirErro("alterar a situação do funcionário", e);
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso aos funcionários.", e);
        }
    }

    @FunctionalInterface
    private interface OperacaoTransacional<T> {
        T executar() throws SQLException;
    }

    private static <T> T emTransacao(Connection conexao, OperacaoTransacional<T> operacao) throws SQLException {
        boolean controlaTransacao = conexao.getAutoCommit();
        if (controlaTransacao) conexao.setAutoCommit(false);
        try {
            T resultado = operacao.executar();
            if (controlaTransacao) conexao.commit();
            return resultado;
        } catch (SQLException | RuntimeException erro) {
            if (controlaTransacao) {
                try {
                    conexao.rollback();
                } catch (SQLException rollbackErro) {
                    erro.addSuppressed(rollbackErro);
                }
            }
            throw erro;
        } finally {
            if (controlaTransacao) conexao.setAutoCommit(true);
        }
    }

    private void exigirPermissao(Sessao sessao) {
        politica.exigir(sessao, Permissao.FUNCIONARIOS);
    }

    private static void validarId(int id) {
        if (id <= 0) throw new DadosInvalidosException("Identificador de funcionário inválido.");
    }

    private static String validarNome(String valor) {
        if (valor == null || valor.isBlank()) throw new DadosInvalidosException("Informe o nome do funcionário.");
        String nome = valor.trim();
        if (nome.length() > 100) throw new DadosInvalidosException("O nome deve possuir no máximo 100 caracteres.");
        return nome;
    }

    private static String validarCpf(String valor) {
        if (valor == null || valor.isBlank()) throw new DadosInvalidosException("Informe o CPF do funcionário.");
        String cpf = valor.replaceAll("\\D", "");
        if (cpf.length() != 11 || cpf.chars().distinct().count() == 1) {
            throw new DadosInvalidosException("Informe um CPF válido.");
        }
        int primeiro = calcularDigito(cpf.substring(0, 9), 10);
        int segundo = calcularDigito(cpf.substring(0, 9) + primeiro, 11);
        if (cpf.charAt(9) - '0' != primeiro || cpf.charAt(10) - '0' != segundo) {
            throw new DadosInvalidosException("Informe um CPF válido.");
        }
        return cpf;
    }

    private static int calcularDigito(String base, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < base.length(); i++) soma += (base.charAt(i) - '0') * (pesoInicial - i);
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static String validarEmail(String valor) {
        if (valor == null || valor.isBlank()) return null;
        String email = valor.trim();
        if (email.length() > 100 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new DadosInvalidosException("Informe um endereço de e-mail válido.");
        }
        return email;
    }

    private static String validarLogin(String valor) {
        if (valor == null || !valor.trim().matches("[A-Za-z0-9._-]{3,50}")) {
            throw new DadosInvalidosException("O login deve ter de 3 a 50 caracteres: letras, números, ponto, hífen ou sublinhado.");
        }
        return valor.trim();
    }

    private static String validarSenha(String valor, boolean obrigatoria) {
        if (valor == null || valor.isEmpty()) {
            if (obrigatoria) throw new DadosInvalidosException("Informe uma senha para o novo funcionário.");
            return null;
        }
        if (valor.length() < 8) throw new DadosInvalidosException("A senha deve possuir ao menos 8 caracteres.");
        if (valor.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new DadosInvalidosException("A senha deve possuir no máximo 72 bytes em UTF-8.");
        }
        return valor;
    }

    private static void validarConfirmacao(String senha, String confirmacao) {
        if (confirmacao == null || !senha.equals(confirmacao)) {
            throw new DadosInvalidosException("A senha e a confirmação não coincidem.");
        }
    }

    private static String validarOpcional(String valor, int limite, String campo) {
        if (valor == null || valor.isBlank()) return null;
        String resultado = valor.trim();
        if (resultado.length() > limite) {
            throw new DadosInvalidosException("O campo " + campo + " deve possuir no máximo " + limite + " caracteres.");
        }
        return resultado;
    }

    private static PerfilFuncionario validarPerfil(PerfilFuncionario perfil) {
        if (perfil == null) throw new DadosInvalidosException("Selecione o perfil do funcionário.");
        return perfil;
    }

    private static RuntimeException traduzirErro(String operacao, SQLException erro) {
        if ("23505".equals(erro.getSQLState())) {
            String detalhe = erro.getMessage() == null ? "" : erro.getMessage().toLowerCase(Locale.ROOT);
            if (detalhe.contains("funcionario_cpf_key")) {
                return new DadosInvalidosException("Já existe um funcionário cadastrado com este CPF.");
            }
            if (detalhe.contains("funcionario_email_key")) {
                return new DadosInvalidosException("Já existe um funcionário cadastrado com este e-mail.");
            }
            if (detalhe.contains("funcionario_login_key")) {
                return new DadosInvalidosException("Este login já está em uso. Escolha outro.");
            }
            return new DadosInvalidosException("Já existe um funcionário com os dados informados.");
        }
        return new FalhaTecnicaException("Não foi possível " + operacao + ".", erro);
    }
}
