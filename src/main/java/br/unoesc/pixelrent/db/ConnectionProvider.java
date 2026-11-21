package br.unoesc.pixelrent.db;

import br.unoesc.pixelrent.config.ConfiguracaoBanco;
import br.unoesc.pixelrent.config.Credencial;
import br.unoesc.pixelrent.config.PerfilConexao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;

/**
 * Abre conexões JDBC com o PostgreSQL usando as credenciais do {@link PerfilConexao} escolhido.
 *
 * <p>Esta classe apenas abre conexões: não controla transações, não executa SQL e não traduz
 * erros. Essas responsabilidades pertencem às etapas seguintes ({@code UnitOfWork} e
 * tradutor de erros). A senha é enviada em {@link Properties}, nunca na URL, e não é registrada
 * em lugar algum. Quem abre a conexão é responsável por fechá-la.</p>
 *
 * <p>O driver ({@code org.postgresql.Driver}) é localizado automaticamente pelo
 * {@link DriverManager} quando está no classpath.</p>
 */
public final class ConnectionProvider {

    /** Nome exibido em {@code pg_stat_activity.application_name}. */
    static final String NOME_APLICACAO = "PixelRent";

    /** Tempo máximo, em segundos, para estabelecer a conexão. */
    static final int TIMEOUT_CONEXAO_SEGUNDOS = 10;

    /**
     * Opção de sessão enviada ao servidor na abertura da conexão: define, como padrão, que as
     * transações da sessão sejam somente leitura. É uma precaução contra escritas acidentais, e
     * não uma barreira de segurança absoluta: a própria sessão pode substituir essa configuração
     * (por exemplo, com {@code SET default_transaction_read_only = off} ou {@code BEGIN READ WRITE}),
     * e ela não substitui os privilégios do role.
     */
    static final String OPCAO_SOMENTE_LEITURA = "-c default_transaction_read_only=on";

    private final ConfiguracaoBanco configuracao;

    /**
     * Cria o provedor.
     *
     * @param configuracao configuração de acesso ao banco
     */
    public ConnectionProvider(ConfiguracaoBanco configuracao) {
        this.configuracao = Objects.requireNonNull(configuracao, "configuracao");
    }

    /**
     * Abre uma conexão comum (leitura e escrita, conforme os privilégios do role).
     *
     * @param perfil perfil cujas credenciais serão usadas
     * @return conexão aberta, com auto-commit ligado (padrão do JDBC)
     * @throws SQLException                                         se o banco recusar a conexão
     * @throws br.unoesc.pixelrent.exception.ConfiguracaoException se o perfil não tiver credenciais
     */
    public Connection abrir(PerfilConexao perfil) throws SQLException {
        return DriverManager.getConnection(configuracao.urlJdbc(), montarPropriedades(perfil, false));
    }

    /**
     * Abre uma conexão cuja sessão inicia com o padrão de transação somente leitura
     * ({@code default_transaction_read_only=on}).
     *
     * <p>Trata-se de uma precaução contra escritas acidentais, não de uma barreira absoluta: a
     * sessão pode substituir essa configuração e ela não substitui os privilégios do role. Quem
     * usa este método deve executar apenas consultas de leitura.</p>
     *
     * @param perfil perfil cujas credenciais serão usadas
     * @return conexão aberta com o padrão somente leitura
     * @throws SQLException                                         se o banco recusar a conexão
     * @throws br.unoesc.pixelrent.exception.ConfiguracaoException se o perfil não tiver credenciais
     */
    public Connection abrirSomenteLeitura(PerfilConexao perfil) throws SQLException {
        return DriverManager.getConnection(configuracao.urlJdbc(), montarPropriedades(perfil, true));
    }

    /**
     * Monta as propriedades de conexão do driver. Visibilidade de pacote para permitir teste
     * sem abrir conexão real.
     */
    Properties montarPropriedades(PerfilConexao perfil, boolean somenteLeitura) {
        Credencial credencial = configuracao.credencial(perfil);

        Properties propriedades = new Properties();
        propriedades.setProperty("user", credencial.usuario());
        propriedades.setProperty("password", credencial.senha());
        propriedades.setProperty("ApplicationName", NOME_APLICACAO);
        propriedades.setProperty("connectTimeout", String.valueOf(TIMEOUT_CONEXAO_SEGUNDOS));
        propriedades.setProperty("loginTimeout", String.valueOf(TIMEOUT_CONEXAO_SEGUNDOS));
        if (somenteLeitura) {
            propriedades.setProperty("options", OPCAO_SOMENTE_LEITURA);
        }
        return propriedades;
    }
}
