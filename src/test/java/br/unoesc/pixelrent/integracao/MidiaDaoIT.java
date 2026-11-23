package br.unoesc.pixelrent.integracao;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.unoesc.pixelrent.config.PerfilConexao;
import br.unoesc.pixelrent.dao.MidiaDao;

import java.sql.Connection;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

/** Consultas somente leitura do DAO de mídias no banco protegido de integração. */
class MidiaDaoIT extends IntegracaoBase {
    private final MidiaDao dao = new MidiaDao();

    @Test
    void listaMidiasSemPesquisa() throws SQLException {
        try (Connection conexao = provider.abrirSomenteLeitura(PerfilConexao.ADMINISTRADOR)) {
            assertNotNull(dao.listar(conexao, null));
        }
    }

    @Test
    void pesquisaMidiaInexistenteRetornaListaVazia() throws SQLException {
        try (Connection conexao = provider.abrirSomenteLeitura(PerfilConexao.ADMINISTRADOR)) {
            assertTrue(dao.listar(conexao, "pixelrent_midia_que_nao_existe_987654").isEmpty());
        }
    }

    @Test
    void listaCombinacoesJogoPlataforma() throws SQLException {
        try (Connection conexao = provider.abrirSomenteLeitura(PerfilConexao.ADMINISTRADOR)) {
            assertNotNull(dao.listarOpcoes(conexao));
        }
    }
}
