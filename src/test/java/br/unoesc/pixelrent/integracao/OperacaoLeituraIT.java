package br.unoesc.pixelrent.integracao;

import static org.junit.jupiter.api.Assertions.assertTrue;

import br.unoesc.pixelrent.config.PerfilConexao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

/** Verificações somente leitura das views e permissões usadas pelo bloco operacional. */
class OperacaoLeituraIT extends IntegracaoBase {

    @Test
    void viewsOperacionaisEstaoDisponiveisParaOsPerfis() throws SQLException {
        for (PerfilConexao perfil : PerfilConexao.values()) {
            try (Connection c = provider.abrirSomenteLeitura(perfil)) {
                assertViewConsultavel(c, "public.vw_locacoes_ativas");
                assertViewConsultavel(c, "public.vw_locacoes_atrasadas");
                assertViewConsultavel(c, "public.vw_historico_cliente");
            }
        }
    }

    @Test
    void proceduresOperacionaisPermitemExecucaoParaOsPerfis() throws SQLException {
        for (PerfilConexao perfil : PerfilConexao.values()) {
            try (Connection c = provider.abrirSomenteLeitura(perfil);
                 PreparedStatement ps = c.prepareStatement("""
                    SELECT
                      has_function_privilege(current_user,
                        'public.sp_realizar_locacao(integer,integer,integer[],integer,integer)', 'EXECUTE'),
                      has_function_privilege(current_user,
                        'public.sp_registrar_devolucao(integer,integer,boolean,numeric)', 'EXECUTE')
                    """);
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertTrue(rs.getBoolean(1), "Perfil sem EXECUTE em sp_realizar_locacao: " + perfil);
                assertTrue(rs.getBoolean(2), "Perfil sem EXECUTE em sp_registrar_devolucao: " + perfil);
            }
        }
    }

    private static void assertViewConsultavel(Connection c, String view) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM " + view);
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next(), "A consulta da view não retornou resultado: " + view);
            assertTrue(rs.getLong(1) >= 0);
        }
    }
}
