
package br.unoesc.pixelrent.integracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.unoesc.pixelrent.config.PerfilConexao;
import br.unoesc.pixelrent.dao.ClienteDao;
import br.unoesc.pixelrent.model.Cliente;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class ClienteDaoIT extends IntegracaoBase {

    private final ClienteDao clienteDao = new ClienteDao();

    @Test
    void listaClientesAtivosEInativos() throws SQLException {

        try (Connection conexao =
                     provider.abrirSomenteLeitura(PerfilConexao.ATENDENTE)) {

            List<Cliente> ativos = clienteDao.listar(conexao, false);
            List<Cliente> todos = clienteDao.listar(conexao, true);

            assertNotNull(ativos);
            assertNotNull(todos);

            assertTrue(todos.containsAll(ativos));
        }
    }

    @Test
    void pesquisaPorNomeExecutaCorretamente() throws SQLException {

        try (Connection conexao =
                     provider.abrirSomenteLeitura(PerfilConexao.ATENDENTE)) {

            List<Cliente> resultado = clienteDao.listar(
                    conexao, true, "pixelrent_cliente_teste_inexistente");

            assertNotNull(resultado);
        }
    }

    @Test
    void pesquisaPorCpfExecutaCorretamente() throws SQLException {

        try (Connection conexao =
                     provider.abrirSomenteLeitura(PerfilConexao.ATENDENTE)) {

            List<Cliente> resultado = clienteDao.listar(
                    conexao, true, "000.000.000-00");

            assertNotNull(resultado);
        }
    }

    @Test
    void buscaClientePeloIdentificador() throws SQLException {

        try (Connection conexao =
                     provider.abrirSomenteLeitura(PerfilConexao.ATENDENTE)) {

            List<Cliente> clientes = clienteDao.listar(conexao, true);

            assertNotNull(clientes);

            if (!clientes.isEmpty()) {
                Cliente esperado = clientes.get(0);

                Optional<Cliente> encontrado =
                        clienteDao.buscarPorId(
                                conexao, esperado.idCliente());

                assertTrue(encontrado.isPresent());
                assertEquals(esperado, encontrado.orElseThrow());
            }
        }
    }
}
