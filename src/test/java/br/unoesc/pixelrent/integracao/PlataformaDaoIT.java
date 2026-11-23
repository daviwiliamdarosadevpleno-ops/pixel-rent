package br.unoesc.pixelrent.integracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.unoesc.pixelrent.config.PerfilConexao;
import br.unoesc.pixelrent.dao.PlataformaDao;
import br.unoesc.pixelrent.model.RegistroCatalogo;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Testes de consulta do DAO de plataformas. Não altera o banco de integração. */
class PlataformaDaoIT extends IntegracaoBase {

    private final PlataformaDao dao = new PlataformaDao();

    @Test
    void listaPlataformasComConexaoSomenteLeitura() throws SQLException {
        try (Connection conexao = provider.abrirSomenteLeitura(PerfilConexao.ATENDENTE)) {
            List<RegistroCatalogo> registros = dao.listar(conexao, null);
            assertNotNull(registros);
            assertTrue(registros.stream().allMatch(item -> item.id() > 0
                    && item.nome() != null && !item.nome().isBlank()));
        }
    }

    @Test
    void pesquisaPorNomeRetornaLista() throws SQLException {
        try (Connection conexao = provider.abrirSomenteLeitura(PerfilConexao.ATENDENTE)) {
            List<RegistroCatalogo> registros = dao.listar(conexao, "__pixelrent_teste_sem_resultado__");
            assertNotNull(registros);
            assertTrue(registros.isEmpty(), "A pesquisa inexistente deve retornar lista vazia.");
        }
    }

    @Test
    void buscaPorIdUmRegistroListavelQuandoExistir() throws SQLException {
        try (Connection conexao = provider.abrirSomenteLeitura(PerfilConexao.ATENDENTE)) {
            List<RegistroCatalogo> registros = dao.listar(conexao, null);
            assertNotNull(registros);
            if (!registros.isEmpty()) {
                RegistroCatalogo esperado = registros.get(0);
                assertEquals(java.util.Optional.of(esperado), dao.buscarPorId(conexao, esperado.id()));
            }
        }
    }
}
