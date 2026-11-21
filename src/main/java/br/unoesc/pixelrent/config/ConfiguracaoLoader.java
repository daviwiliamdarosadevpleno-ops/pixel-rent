package br.unoesc.pixelrent.config;

import br.unoesc.pixelrent.exception.ConfiguracaoException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Carrega a {@link ConfiguracaoBanco} a partir de variáveis de ambiente e de um arquivo local.
 *
 * <p>Ordem de prioridade para cada chave: (1) variável de ambiente
 * ({@link ChavesConfiguracao#variavelDe(String)}); (2) arquivo de propriedades; (3) valor padrão,
 * quando existir. O servidor padrão é {@value #HOST_PADRAO} e a porta padrão é
 * {@value #PORTA_PADRAO}. O nome do banco e as credenciais não têm padrão, de modo que a
 * aplicação nunca se conecta a um banco por acidente.</p>
 *
 * <p>O arquivo é {@code config/pixelrent.properties} (ignorado pelo Git), ou o caminho informado
 * na variável {@value ChavesConfiguracao#VARIAVEL_ARQUIVO}. Valores em branco são tratados como
 * ausentes. Nenhuma mensagem de erro desta classe contém senhas.</p>
 */
public final class ConfiguracaoLoader {

    /** Servidor padrão quando {@code db.host} não é informado. */
    public static final String HOST_PADRAO = "localhost";

    /** Porta padrão quando {@code db.port} não é informada. */
    public static final int PORTA_PADRAO = 5432;

    /** Arquivo local de configuração, relativo ao diretório de trabalho. */
    public static final Path ARQUIVO_PADRAO = Path.of("config", "pixelrent.properties");

    private ConfiguracaoLoader() {
        // Classe utilitária: não instanciável.
    }

    /**
     * Carrega a configuração usando as variáveis de ambiente reais e o arquivo padrão.
     *
     * @return configuração carregada
     * @throws ConfiguracaoException se faltar algum valor obrigatório ou houver valor inválido
     */
    public static ConfiguracaoBanco carregar() {
        return carregar(System.getenv(), ARQUIVO_PADRAO);
    }

    /**
     * Carrega a configuração a partir de um ambiente e de um arquivo informados
     * (útil em testes, sem depender do ambiente real).
     *
     * @param ambiente      mapa que faz o papel das variáveis de ambiente
     * @param arquivoPadrao arquivo usado quando {@value ChavesConfiguracao#VARIAVEL_ARQUIVO}
     *                      não estiver definida; se não existir, é simplesmente ignorado
     * @return configuração carregada
     * @throws ConfiguracaoException se faltar algum valor obrigatório ou houver valor inválido
     */
    public static ConfiguracaoBanco carregar(Map<String, String> ambiente, Path arquivoPadrao) {
        Objects.requireNonNull(ambiente, "ambiente");
        Objects.requireNonNull(arquivoPadrao, "arquivoPadrao");

        Fonte fonte = new Fonte(ambiente, lerArquivo(ambiente, arquivoPadrao));

        String host = fonte.texto(ChavesConfiguracao.HOST);
        String nomeBanco = fonte.texto(ChavesConfiguracao.BANCO);
        if (nomeBanco == null) {
            throw new ConfiguracaoException("Nome do banco não configurado. Defina "
                    + ChavesConfiguracao.BANCO + " ou a variável de ambiente "
                    + ChavesConfiguracao.variavelDe(ChavesConfiguracao.BANCO) + ".");
        }

        Map<PerfilConexao, Credencial> credenciais = new EnumMap<>(PerfilConexao.class);
        for (PerfilConexao perfil : PerfilConexao.values()) {
            String usuario = fonte.texto(perfil.chaveUsuario());
            String senha = fonte.valor(perfil.chaveSenha());
            if (usuario == null && senha == null) {
                continue; // perfil não configurado: só falha se alguém tentar usá-lo
            }
            if (usuario == null || senha == null) {
                throw new ConfiguracaoException("Credencial incompleta para o perfil " + perfil
                        + ": informe " + perfil.chaveUsuario() + " e " + perfil.chaveSenha()
                        + " juntos.");
            }
            credenciais.put(perfil, new Credencial(usuario, senha));
        }

        return new ConfiguracaoBanco(host != null ? host : HOST_PADRAO,
                lerPorta(fonte), nomeBanco, credenciais);
    }

    private static int lerPorta(Fonte fonte) {
        String texto = fonte.texto(ChavesConfiguracao.PORTA);
        if (texto == null) {
            return PORTA_PADRAO;
        }
        try {
            return Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            throw new ConfiguracaoException("A porta do banco (" + ChavesConfiguracao.PORTA
                    + ") deve ser um número inteiro entre 1 e 65535.");
        }
    }

    private static Properties lerArquivo(Map<String, String> ambiente, Path arquivoPadrao) {
        String indicado = ambiente.get(ChavesConfiguracao.VARIAVEL_ARQUIVO);
        boolean explicito = indicado != null && !indicado.isBlank();
        Path caminho = explicito ? Path.of(indicado.trim()) : arquivoPadrao;

        Properties propriedades = new Properties();
        if (!Files.isRegularFile(caminho)) {
            if (explicito) {
                throw new ConfiguracaoException("O arquivo indicado em "
                        + ChavesConfiguracao.VARIAVEL_ARQUIVO + " não existe: " + caminho);
            }
            return propriedades; // sem arquivo local: vale apenas o ambiente
        }
        try (Reader leitor = Files.newBufferedReader(caminho, StandardCharsets.UTF_8)) {
            propriedades.load(leitor);
        } catch (IOException | IllegalArgumentException e) {
            throw new ConfiguracaoException(
                    "Não foi possível ler o arquivo de configuração: " + caminho, e);
        }
        return propriedades;
    }

    /** Consulta valores respeitando a prioridade: variável de ambiente, depois arquivo. */
    private record Fonte(Map<String, String> ambiente, Properties arquivo) {

        /** Valor exatamente como informado, ou {@code null} se ausente/em branco. */
        String valor(String chave) {
            String daVariavel = ambiente.get(ChavesConfiguracao.variavelDe(chave));
            if (daVariavel != null && !daVariavel.isBlank()) {
                return daVariavel;
            }
            String doArquivo = arquivo.getProperty(chave);
            if (doArquivo != null && !doArquivo.isBlank()) {
                return doArquivo;
            }
            return null;
        }

        /** Valor sem espaços nas pontas, ou {@code null} se ausente/em branco. */
        String texto(String chave) {
            String valor = valor(chave);
            return valor == null ? null : valor.trim();
        }
    }
}
