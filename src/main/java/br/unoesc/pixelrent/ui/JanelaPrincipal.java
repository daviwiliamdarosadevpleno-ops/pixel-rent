package br.unoesc.pixelrent.ui;

import br.unoesc.pixelrent.security.ContextoSessao;
import br.unoesc.pixelrent.security.Permissao;
import br.unoesc.pixelrent.security.PoliticaAcesso;
import br.unoesc.pixelrent.security.Sessao;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Font;
import javax.swing.WindowConstants;
import java.util.Objects;

/** Janela principal com menus filtrados conforme o perfil de negócio autenticado. */
@SuppressWarnings("serial")
public final class JanelaPrincipal extends JFrame {

    private static final long serialVersionUID = 1L;

    private final ContextoSessao contextoSessao;
    private final PoliticaAcesso politicaAcesso;
    private final Runnable aoEncerrarSessao;

    public JanelaPrincipal(ContextoSessao contextoSessao, PoliticaAcesso politicaAcesso,
                           Runnable aoEncerrarSessao) {
        super("Pixel Rent");
        this.contextoSessao = Objects.requireNonNull(contextoSessao, "contextoSessao");
        this.politicaAcesso = Objects.requireNonNull(politicaAcesso, "politicaAcesso");
        this.aoEncerrarSessao = Objects.requireNonNull(aoEncerrarSessao, "aoEncerrarSessao");

        Sessao sessao = contextoSessao.exigirSessao();
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setJMenuBar(criarBarraMenu(sessao));
        setContentPane(criarConteudo(sessao));
        setSize(900, 570);
        setLocationRelativeTo(null);
    }

    private JPanel criarConteudo(Sessao sessao) {
        JPanel painel = new JPanel(new BorderLayout(12, 12));
        painel.setBorder(javax.swing.BorderFactory.createEmptyBorder(36, 42, 36, 42));

        JLabel titulo = new JLabel("Bem-vindo ao Pixel Rent, " + sessao.nome() + "!");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 24f));
        JLabel perfil = new JLabel("Perfil: " + sessao.perfil().descricao());
        perfil.setFont(perfil.getFont().deriveFont(Font.PLAIN, 15f));

        JPanel cabecalho = new JPanel(new BorderLayout(0, 8));
        cabecalho.add(titulo, BorderLayout.NORTH);
        cabecalho.add(perfil, BorderLayout.SOUTH);

        JLabel orientacao = new JLabel(
                "Use os menus para acessar as áreas disponíveis para seu perfil.",
                SwingConstants.CENTER);
        painel.add(cabecalho, BorderLayout.NORTH);
        painel.add(orientacao, BorderLayout.CENTER);
        return painel;
    }

    private JMenuBar criarBarraMenu(Sessao sessao) {
        JMenuBar barra = new JMenuBar();

        JMenu cadastros = new JMenu("Cadastros");
        adicionarItem(cadastros, "Clientes", Permissao.CLIENTES, "Etapa 5");
        adicionarItem(cadastros, "Jogos", Permissao.JOGOS, "Etapa 8");
        adicionarItem(cadastros, "Funcionários", Permissao.FUNCIONARIOS, "Etapa 9");
        adicionarItem(cadastros, "Gêneros", Permissao.GENEROS, "Etapa 8");
        adicionarItem(cadastros, "Plataformas", Permissao.PLATAFORMAS, "Etapa 8");
        adicionarItem(cadastros, "Mídias", Permissao.MIDIAS, "Etapa 8");
        adicionarSeTiverItens(barra, cadastros);

        JMenu operacoes = new JMenu("Operações");
        adicionarItem(operacoes, "Nova locação", Permissao.LOCACOES, "Etapa 6");
        adicionarItem(operacoes, "Devoluções", Permissao.DEVOLUCOES, "Etapa 7");
        adicionarItem(operacoes, "Consultas", Permissao.CONSULTAS, "Etapa 4");
        adicionarItem(operacoes, "Manutenção de mídias", Permissao.MANUTENCAO,
                "Funcionalidade planejada; depende da definição e autorização da regra no banco");
        adicionarSeTiverItens(barra, operacoes);

        JMenu relatorios = new JMenu("Relatórios");
        adicionarItem(relatorios, "Jogos mais alugados", Permissao.RELATORIO_JOGOS_MAIS_ALUGADOS,
                "Etapa 10");
        adicionarItem(relatorios, "Faturamento mensal", Permissao.RELATORIO_FATURAMENTO_MENSAL,
                "Etapa 10");
        adicionarItem(relatorios, "Auditoria", Permissao.AUDITORIA, "Etapa 10");
        adicionarSeTiverItens(barra, relatorios);

        JMenu sessaoMenu = new JMenu("Sessão");
        JMenuItem encerrar = new JMenuItem("Encerrar sessão");
        encerrar.addActionListener(evento -> confirmarEncerramento(sessao));
        sessaoMenu.add(encerrar);
        barra.add(sessaoMenu);
        return barra;
    }

    private void adicionarItem(JMenu menu, String titulo, Permissao permissao, String etapa) {
        Sessao sessao = contextoSessao.exigirSessao();
        if (!politicaAcesso.podeAcessar(sessao.perfil(), permissao)) {
            return;
        }
        JMenuItem item = new JMenuItem(titulo);
        item.addActionListener(evento -> JOptionPane.showMessageDialog(this,
                "A tela '" + titulo + "' ainda não foi implementada. Está prevista para " + etapa + ".",
                "Módulo em desenvolvimento", JOptionPane.INFORMATION_MESSAGE));
        menu.add(item);
    }

    private void adicionarSeTiverItens(JMenuBar barra, JMenu menu) {
        if (menu.getItemCount() > 0) {
            barra.add(menu);
        }
    }

    private void confirmarEncerramento(Sessao sessao) {
        int escolha = JOptionPane.showConfirmDialog(this,
                "Deseja encerrar a sessão de " + sessao.nome() + "?",
                "Encerrar sessão", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (escolha == JOptionPane.YES_OPTION) {
            contextoSessao.encerrar();
            dispose();
            aoEncerrarSessao.run();
        }
    }
}
