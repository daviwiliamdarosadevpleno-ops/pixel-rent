package br.unoesc.pixelrent.integracao;

import br.unoesc.pixelrent.config.ConfiguracaoBanco;
import br.unoesc.pixelrent.config.ConfiguracaoLoader;
import br.unoesc.pixelrent.config.PerfilConexao;
import br.unoesc.pixelrent.db.ConnectionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.jupiter.api.BeforeAll;

/**
 * Base de todos os testes de integração (classes {@code *IT}).
 *
 * <p>Garante, antes de qualquer teste, que o acesso é feito somente ao banco
 * {@value #BANCO_PERMITIDO}. O nome do banco é fixado aqui no código: mesmo que a configuração
 * local aponte para {@code pixelrent}, os testes de integração substituem o banco por
 * {@value #BANCO_PERMITIDO}. Depois de conectar, a verificação é repetida com
 * {@code current_database()}, e a execução é abortada se o banco não for o esperado.</p>
 *
 * <p>Os testes de integração executam exclusivamente consultas {@code SELECT} e não realizam
 * alterações no banco. As conexões são abertas com {@code default_transaction_read_only=on}
 * apenas como precaução adicional contra escritas acidentais: isso não é uma barreira absoluta
 * (a sessão pode substituir a configuração) e não substitui os privilégios do role. Todo teste
 * de integração deve estender esta classe.</p>
 */
abstract class IntegracaoBase {

    /** Único banco em que testes de integração podem rodar. */
    static final String BANCO_PERMITIDO = "pixelrent_restore";

    static ConfiguracaoBanco configuracao;
    static ConnectionProvider provider;

    @BeforeAll
    static void verificarQueSoHaAcessoAoBancoRestore() throws SQLException {
        configuracao = ConfiguracaoLoader.carregar().comBanco(BANCO_PERMITIDO);
        provider = new ConnectionProvider(configuracao);

        for (PerfilConexao perfil : PerfilConexao.values()) {
            try (Connection conexao = provider.abrirSomenteLeitura(perfil);
                 PreparedStatement consulta = conexao.prepareStatement("SELECT current_database()");
                 ResultSet resultado = consulta.executeQuery()) {
                resultado.next();
                String banco = resultado.getString(1);
                if (!BANCO_PERMITIDO.equals(banco)) {
                    throw new IllegalStateException("Teste de integração abortado: conectado a '"
                            + banco + "', mas só é permitido '" + BANCO_PERMITIDO + "'.");
                }
            }
        }
    }
}
