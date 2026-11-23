package br.unoesc.pixelrent.integracao;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.unoesc.pixelrent.config.PerfilConexao;
import br.unoesc.pixelrent.dao.JogoDao;

import java.sql.Connection;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

/** Consultas somente leitura do DAO de jogos no banco protegido de integração. */
class JogoDaoIT extends IntegracaoBase {
    private final JogoDao dao = new JogoDao();

    @Test
    void listaJogosSemPesquisa() throws SQLException {
        try (Connection conexao = provider.abrirSomenteLeitura(PerfilConexao.ADMINISTRADOR)) {
            assertNotNull(dao.listar(conexao, null));
        }
    }

    @Test
    void pesquisaJogoInexistenteRetornaListaVazia() throws SQLException {
        try (Connection conexao = provider.abrirSomenteLeitura(PerfilConexao.ADMINISTRADOR)) {
            assertTrue(dao.listar(conexao, "pixelrent_jogo_que_nao_existe_987654").isEmpty());
        }
    }

    @Test
    void listaOpcoesDeGeneroEPlataforma() throws SQLException {
        try (Connection conexao = provider.abrirSomenteLeitura(PerfilConexao.ADMINISTRADOR)) {
            assertNotNull(dao.listarGeneros(conexao));
            assertNotNull(dao.listarPlataformas(conexao));
        }
    }
}
