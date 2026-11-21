package br.unoesc.pixelrent.config;

import java.util.Locale;

/**
 * Nomes das chaves de configuração e a regra de correspondência com variáveis de ambiente.
 *
 * <p>Cada chave do arquivo {@code .properties} possui uma variável de ambiente equivalente,
 * formada pelo prefixo {@code PIXELRENT_}, letras maiúsculas e {@code '.'} trocado por
 * {@code '_'}. Exemplo: {@code db.atendente.password} corresponde a
 * {@code PIXELRENT_DB_ATENDENTE_PASSWORD}.</p>
 */
public final class ChavesConfiguracao {

    /** Servidor do PostgreSQL. */
    public static final String HOST = "db.host";

    /** Porta do PostgreSQL. */
    public static final String PORTA = "db.port";

    /** Nome do banco de dados. */
    public static final String BANCO = "db.name";

    /** Variável de ambiente que indica um caminho alternativo para o arquivo de configuração. */
    public static final String VARIAVEL_ARQUIVO = "PIXELRENT_CONFIG";

    private static final String PREFIXO_VARIAVEL = "PIXELRENT_";

    private ChavesConfiguracao() {
        // Classe utilitária: não instanciável.
    }

    /**
     * Converte uma chave do arquivo de propriedades no nome da variável de ambiente equivalente.
     *
     * @param chave chave no formato {@code db.atendente.user}
     * @return nome da variável, por exemplo {@code PIXELRENT_DB_ATENDENTE_USER}
     */
    public static String variavelDe(String chave) {
        return PREFIXO_VARIAVEL + chave.toUpperCase(Locale.ROOT).replace('.', '_');
    }
}
