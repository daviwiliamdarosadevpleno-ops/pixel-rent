package br.unoesc.pixelrent.integracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.unoesc.pixelrent.config.PerfilConexao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Etapa 1: confirma, por JDBC, a conexão de cada perfil em {@code pixelrent_restore}.
 *
 * <p>Executa exclusivamente consultas {@code SELECT} e não realiza alterações no banco. As
 * conexões usam o padrão somente leitura apenas como precaução adicional (ver
 * {@link IntegracaoBase}); o teste confere que a opção foi aplicada à sessão, sem tratá-la como
 * garantia absoluta.</p>
 */
class ConexaoPerfisIT extends IntegracaoBase {

    @ParameterizedTest(name = "perfil {0}")
    @EnumSource(PerfilConexao.class)
    void conectaNoBancoRestoreComOUsuarioDoPerfil(PerfilConexao perfil) throws SQLException {
        String usuarioEsperado = configuracao.credencial(perfil).usuario();

        try (Connection conexao = provider.abrirSomenteLeitura(perfil);
             PreparedStatement consulta = conexao.prepareStatement(
                     "SELECT current_database(), current_user, "
                             + "current_setting('transaction_read_only'), "
                             + "current_setting('server_version')");
             ResultSet resultado = consulta.executeQuery()) {

            assertTrue(resultado.next());
            String banco = resultado.getString(1);
            String usuario = resultado.getString(2);
            String somenteLeitura = resultado.getString(3);
            String versaoServidor = resultado.getString(4);

            System.out.printf("[%s] banco=%s usuario=%s somente_leitura=%s servidor=%s%n",
                    perfil, banco, usuario, somenteLeitura, versaoServidor);

            assertEquals(BANCO_PERMITIDO, banco);
            assertEquals(usuarioEsperado, usuario);
            assertEquals("on", somenteLeitura);
        }
    }

    @ParameterizedTest(name = "perfil {0}")
    @EnumSource(PerfilConexao.class)
    void usuarioPertenceSomenteAoGrupoDoSeuPerfil(PerfilConexao perfil) throws SQLException {
        try (Connection conexao = provider.abrirSomenteLeitura(perfil);
             PreparedStatement consulta = conexao.prepareStatement(
                     "SELECT pg_has_role(current_user, 'grp_atendente', 'USAGE'), "
                             + "pg_has_role(current_user, 'grp_admin', 'USAGE')");
             ResultSet resultado = consulta.executeQuery()) {

            assertTrue(resultado.next());
            boolean noGrupoAtendente = resultado.getBoolean(1);
            boolean noGrupoAdmin = resultado.getBoolean(2);

            assertEquals(perfil == PerfilConexao.ATENDENTE, noGrupoAtendente,
                    "associação a grp_atendente do perfil " + perfil);
            assertEquals(perfil == PerfilConexao.ADMINISTRADOR, noGrupoAdmin,
                    "associação a grp_admin do perfil " + perfil);
        }
    }
}
