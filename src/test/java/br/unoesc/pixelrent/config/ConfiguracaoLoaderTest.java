package br.unoesc.pixelrent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.unoesc.pixelrent.exception.ConfiguracaoException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Testes unitários (sem banco) da leitura de configuração. */
class ConfiguracaoLoaderTest {

    private static final Path SEM_ARQUIVO = Path.of("pasta-inexistente", "pixelrent.properties");

    @Test
    void usaPadroesParaHostEPorta() {
        ConfiguracaoBanco config = ConfiguracaoLoader.carregar(
                Map.of("PIXELRENT_DB_NAME", "pixelrent_restore"), SEM_ARQUIVO);

        assertEquals("localhost", config.getHost());
        assertEquals(5432, config.getPorta());
        assertEquals("pixelrent_restore", config.getNomeBanco());
    }

    @Test
    void leCredenciaisDasVariaveisDeAmbiente() {
        ConfiguracaoBanco config = ConfiguracaoLoader.carregar(Map.of(
                "PIXELRENT_DB_NAME", "pixelrent_restore",
                "PIXELRENT_DB_ATENDENTE_USER", "usuario_a",
                "PIXELRENT_DB_ATENDENTE_PASSWORD", "senha_a"), SEM_ARQUIVO);

        assertTrue(config.possuiCredencial(PerfilConexao.ATENDENTE));
        assertFalse(config.possuiCredencial(PerfilConexao.ADMINISTRADOR));
        assertEquals("usuario_a", config.credencial(PerfilConexao.ATENDENTE).usuario());
        assertEquals("senha_a", config.credencial(PerfilConexao.ATENDENTE).senha());
    }

    @Test
    void leTodosOsValoresDoArquivo(@TempDir Path pasta) throws IOException {
        Path arquivo = pasta.resolve("pixelrent.properties");
        Files.writeString(arquivo, String.join("\n",
                "db.host=servidor-teste",
                "db.port=5433",
                "db.name=pixelrent_restore",
                "db.atendente.user=usuario_a",
                "db.atendente.password=senha_a",
                "db.admin.user=usuario_b",
                "db.admin.password=senha_b"), StandardCharsets.UTF_8);

        ConfiguracaoBanco config = ConfiguracaoLoader.carregar(Map.of(), arquivo);

        assertEquals("servidor-teste", config.getHost());
        assertEquals(5433, config.getPorta());
        assertEquals("usuario_b", config.credencial(PerfilConexao.ADMINISTRADOR).usuario());
        assertEquals("senha_b", config.credencial(PerfilConexao.ADMINISTRADOR).senha());
    }

    @Test
    void variavelDeAmbienteTemPrioridadeSobreOArquivo(@TempDir Path pasta) throws IOException {
        Path arquivo = pasta.resolve("pixelrent.properties");
        Files.writeString(arquivo, "db.name=banco_do_arquivo\ndb.host=host_do_arquivo\n",
                StandardCharsets.UTF_8);

        ConfiguracaoBanco config = ConfiguracaoLoader.carregar(
                Map.of("PIXELRENT_DB_NAME", "banco_da_variavel"), arquivo);

        assertEquals("banco_da_variavel", config.getNomeBanco());
        assertEquals("host_do_arquivo", config.getHost());
    }

    @Test
    void variavelEmBrancoCaiNoArquivo(@TempDir Path pasta) throws IOException {
        Path arquivo = pasta.resolve("pixelrent.properties");
        Files.writeString(arquivo, "db.name=banco_do_arquivo\n", StandardCharsets.UTF_8);

        ConfiguracaoBanco config = ConfiguracaoLoader.carregar(
                Map.of("PIXELRENT_DB_NAME", "   "), arquivo);

        assertEquals("banco_do_arquivo", config.getNomeBanco());
    }

    @Test
    void exigeONomeDoBanco() {
        ConfiguracaoException erro = assertThrows(ConfiguracaoException.class,
                () -> ConfiguracaoLoader.carregar(Map.of(), SEM_ARQUIVO));

        assertTrue(erro.getMessage().contains("db.name"));
        assertTrue(erro.getMessage().contains("PIXELRENT_DB_NAME"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "65536", "-1", "54 32"})
    void rejeitaPortaInvalida(String porta) {
        assertThrows(ConfiguracaoException.class, () -> ConfiguracaoLoader.carregar(Map.of(
                "PIXELRENT_DB_NAME", "pixelrent_restore",
                "PIXELRENT_DB_PORT", porta), SEM_ARQUIVO));
    }

    @Test
    void rejeitaCredencialIncompletaSemRevelarOValor() {
        ConfiguracaoException erro = assertThrows(ConfiguracaoException.class,
                () -> ConfiguracaoLoader.carregar(Map.of(
                        "PIXELRENT_DB_NAME", "pixelrent_restore",
                        "PIXELRENT_DB_ADMIN_PASSWORD", "segredo-que-nao-pode-vazar"), SEM_ARQUIVO));

        assertTrue(erro.getMessage().contains("db.admin.user"));
        assertFalse(erro.getMessage().contains("segredo-que-nao-pode-vazar"));
    }

    @Test
    void arquivoIndicadoPelaVariavelDeveExistir(@TempDir Path pasta) {
        Path inexistente = pasta.resolve("nao-existe.properties");

        assertThrows(ConfiguracaoException.class, () -> ConfiguracaoLoader.carregar(Map.of(
                "PIXELRENT_CONFIG", inexistente.toString(),
                "PIXELRENT_DB_NAME", "pixelrent_restore"), SEM_ARQUIVO));
    }

    @Test
    void arquivoIndicadoPelaVariavelSubstituiOPadrao(@TempDir Path pasta) throws IOException {
        Path outro = pasta.resolve("outro.properties");
        Files.writeString(outro, "db.name=banco_de_outro_arquivo\n", StandardCharsets.UTF_8);

        ConfiguracaoBanco config = ConfiguracaoLoader.carregar(
                Map.of("PIXELRENT_CONFIG", outro.toString()), SEM_ARQUIVO);

        assertEquals("banco_de_outro_arquivo", config.getNomeBanco());
    }

    @Test
    void modeloDeExemploNaoDefineCredenciais() throws IOException {
        Path exemplo = Path.of("config", "pixelrent.properties.example");
        if (!Files.isRegularFile(exemplo)) {
            return; // teste executado fora da raiz do projeto: nada a verificar
        }

        ConfiguracaoBanco config = ConfiguracaoLoader.carregar(Map.of(), exemplo);

        assertEquals("pixelrent_restore", config.getNomeBanco());
        assertFalse(config.possuiCredencial(PerfilConexao.ATENDENTE));
        assertFalse(config.possuiCredencial(PerfilConexao.ADMINISTRADOR));
    }
}
