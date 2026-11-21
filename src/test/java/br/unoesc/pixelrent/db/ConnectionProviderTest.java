package br.unoesc.pixelrent.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.unoesc.pixelrent.config.ConfiguracaoBanco;
import br.unoesc.pixelrent.config.Credencial;
import br.unoesc.pixelrent.config.PerfilConexao;
import br.unoesc.pixelrent.exception.ConfiguracaoException;

import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/** Testes unitários (sem abrir conexão) da montagem das propriedades de conexão. */
class ConnectionProviderTest {

    private static ConnectionProvider provedorComAtendente() {
        ConfiguracaoBanco config = new ConfiguracaoBanco("localhost", 5432, "pixelrent_restore",
                Map.of(PerfilConexao.ATENDENTE, new Credencial("usuario_a", "senha-secreta-123")));
        return new ConnectionProvider(config);
    }

    @Test
    void propriedadesTrazemUsuarioSenhaENomeDaAplicacao() {
        Properties p = provedorComAtendente().montarPropriedades(PerfilConexao.ATENDENTE, false);

        assertEquals("usuario_a", p.getProperty("user"));
        assertEquals("senha-secreta-123", p.getProperty("password"));
        assertEquals("PixelRent", p.getProperty("ApplicationName"));
        assertEquals("10", p.getProperty("connectTimeout"));
    }

    @Test
    void conexaoComumNaoForcaSomenteLeitura() {
        Properties p = provedorComAtendente().montarPropriedades(PerfilConexao.ATENDENTE, false);

        assertNull(p.getProperty("options"));
    }

    @Test
    void conexaoSomenteLeituraPedeOModoAoServidor() {
        Properties p = provedorComAtendente().montarPropriedades(PerfilConexao.ATENDENTE, true);

        assertEquals("-c default_transaction_read_only=on", p.getProperty("options"));
    }

    @Test
    void perfilSemCredencialFalhaAntesDeConectar() {
        ConnectionProvider provedor = provedorComAtendente();

        assertThrows(ConfiguracaoException.class,
                () -> provedor.montarPropriedades(PerfilConexao.ADMINISTRADOR, false));
        assertThrows(ConfiguracaoException.class,
                () -> provedor.abrir(PerfilConexao.ADMINISTRADOR));
    }
}
