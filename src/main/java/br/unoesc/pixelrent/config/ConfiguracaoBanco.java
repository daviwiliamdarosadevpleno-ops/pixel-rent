package br.unoesc.pixelrent.config;

import br.unoesc.pixelrent.exception.ConfiguracaoException;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuração imutável de acesso ao PostgreSQL: servidor, porta, nome do banco e as
 * credenciais de cada {@link PerfilConexao}.
 *
 * <p>Instâncias são normalmente obtidas por {@link ConfiguracaoLoader}. A classe não
 * expõe senhas em {@link #toString()} nem na URL JDBC.</p>
 */
public final class ConfiguracaoBanco {

    private static final int PORTA_MINIMA = 1;
    private static final int PORTA_MAXIMA = 65535;

    private final String host;
    private final int porta;
    private final String nomeBanco;
    private final Map<PerfilConexao, Credencial> credenciais;

    /**
     * Cria a configuração.
     *
     * @param host        servidor do PostgreSQL (não vazio)
     * @param porta       porta TCP, entre 1 e 65535
     * @param nomeBanco   nome do banco de dados (não vazio)
     * @param credenciais credenciais por perfil; pode estar vazio ou incompleto
     * @throws ConfiguracaoException se algum valor for inválido
     */
    public ConfiguracaoBanco(String host, int porta, String nomeBanco,
                             Map<PerfilConexao, Credencial> credenciais) {
        if (host == null || host.isBlank()) {
            throw new ConfiguracaoException("O servidor do banco (" + ChavesConfiguracao.HOST
                    + ") não pode ser vazio.");
        }
        if (porta < PORTA_MINIMA || porta > PORTA_MAXIMA) {
            throw new ConfiguracaoException("A porta do banco (" + ChavesConfiguracao.PORTA
                    + ") deve estar entre " + PORTA_MINIMA + " e " + PORTA_MAXIMA + ".");
        }
        if (nomeBanco == null || nomeBanco.isBlank()) {
            throw new ConfiguracaoException("O nome do banco (" + ChavesConfiguracao.BANCO
                    + ") não pode ser vazio.");
        }
        this.host = host.trim();
        this.porta = porta;
        this.nomeBanco = nomeBanco.trim();

        Map<PerfilConexao, Credencial> copia = new EnumMap<>(PerfilConexao.class);
        if (credenciais != null) {
            copia.putAll(credenciais);
        }
        this.credenciais = Collections.unmodifiableMap(copia);
    }

    /**
     * Servidor do PostgreSQL.
     *
     * @return nome ou endereço do servidor
     */
    public String getHost() {
        return host;
    }

    /**
     * Porta TCP do PostgreSQL.
     *
     * @return número da porta
     */
    public int getPorta() {
        return porta;
    }

    /**
     * Nome do banco de dados.
     *
     * @return por exemplo {@code pixelrent_restore}
     */
    public String getNomeBanco() {
        return nomeBanco;
    }

    /**
     * Indica se há credenciais configuradas para o perfil.
     *
     * @param perfil perfil de conexão
     * @return {@code true} se usuário e senha foram informados
     */
    public boolean possuiCredencial(PerfilConexao perfil) {
        return credenciais.containsKey(Objects.requireNonNull(perfil, "perfil"));
    }

    /**
     * Obtém as credenciais de um perfil.
     *
     * @param perfil perfil de conexão
     * @return credenciais do perfil
     * @throws ConfiguracaoException se o perfil não foi configurado; a mensagem informa
     *                               quais chaves e variáveis de ambiente devem ser definidas
     */
    public Credencial credencial(PerfilConexao perfil) {
        Credencial credencial = credenciais.get(Objects.requireNonNull(perfil, "perfil"));
        if (credencial == null) {
            throw new ConfiguracaoException("Credenciais do perfil " + perfil + " não configuradas. "
                    + "Defina " + perfil.chaveUsuario() + " e " + perfil.chaveSenha()
                    + " no arquivo de configuração, ou as variáveis de ambiente "
                    + ChavesConfiguracao.variavelDe(perfil.chaveUsuario()) + " e "
                    + ChavesConfiguracao.variavelDe(perfil.chaveSenha()) + ".");
        }
        return credencial;
    }

    /**
     * Cria uma cópia desta configuração apontando para outro banco de dados,
     * mantendo servidor, porta e credenciais.
     *
     * @param outroBanco nome do outro banco
     * @return nova configuração
     */
    public ConfiguracaoBanco comBanco(String outroBanco) {
        return new ConfiguracaoBanco(host, porta, outroBanco, credenciais);
    }

    /**
     * Monta a URL JDBC (sem usuário nem senha).
     *
     * @return por exemplo {@code jdbc:postgresql://localhost:5432/pixelrent_restore}
     */
    public String urlJdbc() {
        String hostNaUrl = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
        return "jdbc:postgresql://" + hostNaUrl + ":" + porta + "/" + nomeBanco;
    }

    @Override
    public String toString() {
        return "ConfiguracaoBanco[host=" + host + ", porta=" + porta + ", banco=" + nomeBanco
                + ", perfisConfigurados=" + credenciais.keySet() + "]";
    }
}
