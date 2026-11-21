package br.unoesc.pixelrent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.unoesc.pixelrent.exception.ConfiguracaoException;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** Testes unitários (sem banco) da configuração imutável e da credencial. */
class ConfiguracaoBancoTest {

    private static ConfiguracaoBanco configuracaoComAtendente() {
        return new ConfiguracaoBanco("localhost", 5432, "pixelrent_restore",
                Map.of(PerfilConexao.ATENDENTE, new Credencial("usuario_a", "senha-secreta-123")));
    }

    @Test
    void urlJdbcNaoContemUsuarioNemSenha() {
        String url = configuracaoComAtendente().urlJdbc();

        assertEquals("jdbc:postgresql://localhost:5432/pixelrent_restore", url);
        assertFalse(url.contains("senha-secreta-123"));
        assertFalse(url.contains("usuario_a"));
    }

    @Test
    void urlJdbcColocaEnderecoIpv6EntreColchetes() {
        ConfiguracaoBanco config = new ConfiguracaoBanco("::1", 5432, "pixelrent_restore", Map.of());

        assertEquals("jdbc:postgresql://[::1]:5432/pixelrent_restore", config.urlJdbc());
    }

    @Test
    void credencialAusenteInformaAsChavesESemSegredos() {
        ConfiguracaoBanco config = configuracaoComAtendente();

        ConfiguracaoException erro = assertThrows(ConfiguracaoException.class,
                () -> config.credencial(PerfilConexao.ADMINISTRADOR));

        assertTrue(erro.getMessage().contains("db.admin.user"));
        assertTrue(erro.getMessage().contains("PIXELRENT_DB_ADMIN_PASSWORD"));
        assertFalse(erro.getMessage().contains("senha-secreta-123"));
    }

    @Test
    void comBancoTrocaSomenteONomeDoBanco() {
        ConfiguracaoBanco original = configuracaoComAtendente();

        ConfiguracaoBanco copia = original.comBanco("outro_banco");

        assertEquals("outro_banco", copia.getNomeBanco());
        assertEquals("pixelrent_restore", original.getNomeBanco());
        assertEquals(original.getHost(), copia.getHost());
        assertEquals(original.getPorta(), copia.getPorta());
        assertEquals("senha-secreta-123", copia.credencial(PerfilConexao.ATENDENTE).senha());
    }

    @Test
    void toStringNaoExpoeSenhas() {
        ConfiguracaoBanco config = configuracaoComAtendente();

        assertFalse(config.toString().contains("senha-secreta-123"));
        assertFalse(config.credencial(PerfilConexao.ATENDENTE).toString().contains("senha-secreta-123"));
    }

    @Test
    void rejeitaValoresInvalidos() {
        assertThrows(ConfiguracaoException.class,
                () -> new ConfiguracaoBanco(" ", 5432, "pixelrent_restore", Map.of()));
        assertThrows(ConfiguracaoException.class,
                () -> new ConfiguracaoBanco("localhost", 0, "pixelrent_restore", Map.of()));
        assertThrows(ConfiguracaoException.class,
                () -> new ConfiguracaoBanco("localhost", 70000, "pixelrent_restore", Map.of()));
        assertThrows(ConfiguracaoException.class,
                () -> new ConfiguracaoBanco("localhost", 5432, "", Map.of()));
    }

    @Test
    void credencialExigeUsuarioESenha() {
        assertThrows(IllegalArgumentException.class, () -> new Credencial(" ", "x"));
        assertThrows(IllegalArgumentException.class, () -> new Credencial("u", ""));
        assertThrows(IllegalArgumentException.class, () -> new Credencial(null, "x"));
    }
}
