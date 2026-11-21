package br.unoesc.pixelrent.ui;

import br.unoesc.pixelrent.exception.AcessoNegadoException;
import br.unoesc.pixelrent.exception.DadosInvalidosException;
import br.unoesc.pixelrent.exception.FalhaTecnicaException;
import br.unoesc.pixelrent.security.Sessao;
import br.unoesc.pixelrent.service.AutenticacaoService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/** Janela modal de login; consultas JDBC são executadas fora da Event Dispatch Thread. */
@SuppressWarnings("serial")
public final class LoginDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final AutenticacaoService autenticacaoService;
    private final JTextField campoLogin = new JTextField(22);
    private final JPasswordField campoSenha = new JPasswordField(22);
    private final JButton botaoEntrar = new JButton("Entrar");
    private final JButton botaoCancelar = new JButton("Cancelar");
    private final JLabel mensagem = new JLabel("Informe suas credenciais de funcionário.");

    private Sessao sessaoAutenticada;
    private boolean processando;

    public LoginDialog(Window proprietario, AutenticacaoService autenticacaoService) {
        super(proprietario, "Pixel Rent - Entrar", Dialog.ModalityType.APPLICATION_MODAL);
        this.autenticacaoService = autenticacaoService;
        construirInterface();
        configurarEventos();
    }

    /** @return sessão obtida no login; vazio quando o diálogo foi cancelado */
    public Optional<Sessao> sessaoAutenticada() {
        return Optional.ofNullable(sessaoAutenticada);
    }

    private void construirInterface() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        JLabel titulo = new JLabel("PIXEL RENT");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 25f));
        JLabel subtitulo = new JLabel("Acesso ao sistema de locação");

        JPanel cabecalho = new JPanel(new BorderLayout(0, 5));
        cabecalho.setBorder(BorderFactory.createEmptyBorder(22, 26, 12, 26));
        cabecalho.add(titulo, BorderLayout.NORTH);
        cabecalho.add(subtitulo, BorderLayout.SOUTH);

        JPanel formulario = new JPanel(new GridBagLayout());
        formulario.setBorder(BorderFactory.createEmptyBorder(8, 26, 8, 26));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 4, 6, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;
        formulario.add(new JLabel("Login"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        formulario.add(campoLogin, c);
        c.gridx = 0;
        c.gridy = 1;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        formulario.add(new JLabel("Senha"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        formulario.add(campoSenha, c);

        mensagem.setBorder(BorderFactory.createEmptyBorder(4, 26, 4, 26));

        JPanel botoes = new JPanel();
        botoes.setBorder(BorderFactory.createEmptyBorder(8, 20, 18, 20));
        botoes.add(botaoCancelar);
        botoes.add(botaoEntrar);

        JPanel centro = new JPanel(new BorderLayout());
        centro.add(formulario, BorderLayout.CENTER);
        centro.add(mensagem, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(cabecalho, BorderLayout.NORTH);
        add(centro, BorderLayout.CENTER);
        add(botoes, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(botaoEntrar);
        setMinimumSize(new Dimension(430, 265));
        pack();
        setLocationRelativeTo(getOwner());
    }

    private void configurarEventos() {
        botaoEntrar.addActionListener(evento -> autenticar());
        botaoCancelar.addActionListener(evento -> dispose());
        campoSenha.addActionListener(evento -> autenticar());
    }

    private void autenticar() {
        if (processando) {
            return;
        }

        String login = campoLogin.getText().trim();
        char[] senha = campoSenha.getPassword();
        campoSenha.setText("");

        setProcessando(true);
        mensagem.setText("Validando credenciais...");

        SwingWorker<Sessao, Void> tarefa = new SwingWorker<>() {
            @Override
            protected Sessao doInBackground() {
                return autenticacaoService.autenticar(login, senha);
            }

            @Override
            protected void done() {
                try {
                    sessaoAutenticada = get();
                    dispose();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    setProcessando(false);
                    mensagem.setText("A operação foi interrompida.");
                } catch (ExecutionException e) {
                    setProcessando(false);
                    mostrarFalha(e.getCause());
                } finally {
                    Arrays.fill(senha, '\0');
                }
            }
        };
        tarefa.execute();
    }

    private void setProcessando(boolean valor) {
        processando = valor;
        campoLogin.setEnabled(!valor);
        campoSenha.setEnabled(!valor);
        botaoEntrar.setEnabled(!valor);
        botaoCancelar.setEnabled(!valor);
        setCursor(valor
                ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR)
                : java.awt.Cursor.getDefaultCursor());
    }

    private void mostrarFalha(Throwable causa) {
        if (causa instanceof DadosInvalidosException) {
            mensagem.setText("Informe o login e a senha.");
            campoLogin.requestFocusInWindow();
            return;
        }
        if (causa instanceof AcessoNegadoException) {
            // Não diferencia login inexistente, senha incorreta ou funcionário inativo.
            mensagem.setText("Login ou senha inválidos.");
            campoSenha.requestFocusInWindow();
            return;
        }
        if (causa instanceof FalhaTecnicaException) {
            mensagem.setText("Não foi possível acessar o banco de dados.");
            JOptionPane.showMessageDialog(this,
                    "Não foi possível validar o login. Confira a conexão e o arquivo "
                            + "config/pixelrent.properties.",
                    "Falha de conexão", JOptionPane.ERROR_MESSAGE);
            return;
        }
        mensagem.setText("Não foi possível concluir o login.");
        JOptionPane.showMessageDialog(this, "Ocorreu uma falha inesperada durante o login.",
                "Falha", JOptionPane.ERROR_MESSAGE);
    }
}
