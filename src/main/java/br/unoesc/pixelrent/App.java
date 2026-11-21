package br.unoesc.pixelrent;

import br.unoesc.pixelrent.config.ConfiguracaoBanco;
import br.unoesc.pixelrent.config.ConfiguracaoLoader;
import br.unoesc.pixelrent.dao.AutenticacaoDao;
import br.unoesc.pixelrent.db.ConnectionProvider;
import br.unoesc.pixelrent.exception.PixelRentException;
import br.unoesc.pixelrent.security.ContextoSessao;
import br.unoesc.pixelrent.security.PoliticaAcesso;
import br.unoesc.pixelrent.security.Sessao;
import br.unoesc.pixelrent.service.AutenticacaoService;
import br.unoesc.pixelrent.ui.JanelaPrincipal;
import br.unoesc.pixelrent.ui.LoginDialog;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Ponto de entrada da aplicação desktop Pixel Rent. */
public final class App {

    private App() {
        // Classe de inicialização.
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(App::iniciar);
    }

    private static void iniciar() {
        try {
            ConfiguracaoBanco configuracao = ConfiguracaoLoader.carregar();
            ConnectionProvider connectionProvider = new ConnectionProvider(configuracao);
            AutenticacaoService autenticacaoService = new AutenticacaoService(
                    connectionProvider, new AutenticacaoDao());
            new ControladorAplicacao(autenticacaoService).mostrarLogin();
        } catch (PixelRentException e) {
            JOptionPane.showMessageDialog(null, e.getMessage(),
                    "Configuração do Pixel Rent", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Coordena a troca entre login, janela principal e logout sem estado global. */
    private static final class ControladorAplicacao {

        private final AutenticacaoService autenticacaoService;
        private final ContextoSessao contextoSessao = new ContextoSessao();
        private final PoliticaAcesso politicaAcesso = new PoliticaAcesso();

        private ControladorAplicacao(AutenticacaoService autenticacaoService) {
            this.autenticacaoService = autenticacaoService;
        }

        private void mostrarLogin() {
            LoginDialog dialogo = new LoginDialog(null, autenticacaoService);
            dialogo.setVisible(true);
            Sessao sessao = dialogo.sessaoAutenticada().orElse(null);
            if (sessao == null) {
                return; // usuário cancelou o login ou fechou a janela.
            }

            contextoSessao.iniciar(sessao);
            JanelaPrincipal principal = new JanelaPrincipal(
                    contextoSessao, politicaAcesso, this::agendarNovoLogin);
            principal.setVisible(true);
        }

        private void agendarNovoLogin() {
            SwingUtilities.invokeLater(this::mostrarLogin);
        }
    }
}
