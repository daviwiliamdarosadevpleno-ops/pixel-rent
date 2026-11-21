package br.unoesc.pixelrent.service;

import br.unoesc.pixelrent.exception.ConfiguracaoException;
import br.unoesc.pixelrent.config.PerfilConexao;
import br.unoesc.pixelrent.dao.AutenticacaoDao;
import br.unoesc.pixelrent.db.ConnectionProvider;
import br.unoesc.pixelrent.exception.AcessoNegadoException;
import br.unoesc.pixelrent.exception.DadosInvalidosException;
import br.unoesc.pixelrent.exception.FalhaTecnicaException;
import br.unoesc.pixelrent.security.Sessao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Objects;

/** Caso de uso de autenticação; abre e fecha a conexão usada exclusivamente para o login. */
public final class AutenticacaoService {

    private final ConnectionProvider connectionProvider;
    private final AutenticacaoDao autenticacaoDao;

    public AutenticacaoService(ConnectionProvider connectionProvider, AutenticacaoDao autenticacaoDao) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
        this.autenticacaoDao = Objects.requireNonNull(autenticacaoDao, "autenticacaoDao");
    }

    /**
     * Autentica um funcionário por meio de {@code public.fn_autenticar_funcionario}.
     * Sempre usa inicialmente o role do atendente, que possui EXECUTE na função e é o
     * perfil de conexão de menor privilégio. A conexão é fechada antes do retorno.
     *
     * @param login login do funcionário
     * @param senha array obtido de {@code JPasswordField#getPassword}; será apagado pelo método
     * @return sessão imutável, sem senha ou hash
     * @throws AcessoNegadoException se login/senha não forem aceitos
     * @throws DadosInvalidosException se os campos obrigatórios estiverem vazios
     * @throws FalhaTecnicaException se não for possível consultar o banco
     */
    public Sessao autenticar(String login, char[] senha) {
        try {
            if (login == null || login.isBlank()) {
                throw new DadosInvalidosException("Informe o login.");
            }
            if (senha == null || senha.length == 0) {
                throw new DadosInvalidosException("Informe a senha.");
            }

            try (Connection conexao = connectionProvider.abrirSomenteLeitura(PerfilConexao.ATENDENTE)) {
                return autenticacaoDao.autenticar(conexao, login.trim(), senha)
                        .orElseThrow(() -> new AcessoNegadoException("Login ou senha inválidos."));
            }
        } catch (SQLException | ConfiguracaoException e) {
            throw new FalhaTecnicaException(
                    "Não foi possível acessar o banco para validar o login. Confira a configuração e a conexão.",
                    e);
        } finally {
            if (senha != null) {
                Arrays.fill(senha, '\0');
            }
        }
    }
}
