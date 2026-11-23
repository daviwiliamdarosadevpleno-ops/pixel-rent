package br.unoesc.pixelrent.service;

import br.unoesc.pixelrent.dao.JogoDao;
import br.unoesc.pixelrent.db.ConnectionProvider;
import br.unoesc.pixelrent.exception.ConfiguracaoException;
import br.unoesc.pixelrent.exception.DadosInvalidosException;
import br.unoesc.pixelrent.exception.FalhaTecnicaException;
import br.unoesc.pixelrent.model.Jogo;
import br.unoesc.pixelrent.model.RegistroCatalogo;
import br.unoesc.pixelrent.security.PerfilFuncionario;
import br.unoesc.pixelrent.security.Permissao;
import br.unoesc.pixelrent.security.PoliticaAcesso;
import br.unoesc.pixelrent.security.Sessao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Regras de negócio e autorização do catálogo de jogos. */
public final class JogoService {
    private final ConnectionProvider provider;
    private final JogoDao dao;
    private final PoliticaAcesso politica;

    public JogoService(ConnectionProvider provider, JogoDao dao, PoliticaAcesso politica) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.dao = Objects.requireNonNull(dao, "dao");
        this.politica = Objects.requireNonNull(politica, "politica");
    }

    public List<Jogo> listar(Sessao sessao, String pesquisa) {
        exigir(sessao);
        try (Connection conexao = provider.abrirSomenteLeitura(sessao.perfilConexao())) {
            return dao.listar(conexao, pesquisa);
        } catch (SQLException e) {
            throw traduzir(e, "consultar jogos", false);
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso aos jogos.", e);
        }
    }

    public Optional<Jogo> buscarPorId(Sessao sessao, int id) {
        exigir(sessao);
        validarId(id);
        try (Connection conexao = provider.abrirSomenteLeitura(sessao.perfilConexao())) {
            return dao.buscarPorId(conexao, id);
        } catch (SQLException e) {
            throw traduzir(e, "consultar o jogo", false);
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso aos jogos.", e);
        }
    }

    public List<RegistroCatalogo> listarGeneros(Sessao sessao) {
        exigir(sessao);
        try (Connection conexao = provider.abrirSomenteLeitura(sessao.perfilConexao())) {
            return dao.listarGeneros(conexao);
        } catch (SQLException e) {
            throw traduzir(e, "consultar gêneros", false);
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso aos gêneros.", e);
        }
    }

    public List<RegistroCatalogo> listarPlataformas(Sessao sessao) {
        exigir(sessao);
        try (Connection conexao = provider.abrirSomenteLeitura(sessao.perfilConexao())) {
            return dao.listarPlataformas(conexao);
        } catch (SQLException e) {
            throw traduzir(e, "consultar plataformas", false);
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso às plataformas.", e);
        }
    }

    public int cadastrar(Sessao sessao, String titulo, Integer ano,
                         String classificacao, Integer idGenero, List<Integer> plataformas) {
        exigir(sessao);
        Dados dados = validar(titulo, ano, classificacao, idGenero, plataformas, true);
        try (Connection conexao = provider.abrir(sessao.perfilConexao())) {
            conexao.setAutoCommit(false);
            try {
                int id = dao.inserir(conexao, dados.titulo(), dados.ano(),
                        dados.classificacao(), dados.idGenero());
                dao.adicionarPlataformas(conexao, id, dados.plataformas());
                conexao.commit();
                return id;
            } catch (SQLException | RuntimeException e) {
                rollback(conexao, e);
                throw e;
            }
        } catch (SQLException e) {
            throw traduzir(e, "cadastrar o jogo", false);
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso aos jogos.", e);
        }
    }

    public boolean atualizar(Sessao sessao, int id, String titulo, Integer ano,
                             String classificacao, Integer idGenero, List<Integer> plataformas) {
        exigir(sessao);
        validarId(id);
        boolean administrador = sessao.perfil() == PerfilFuncionario.ADMINISTRADOR;
        Dados dados = validar(titulo, ano, classificacao, idGenero, plataformas, administrador);
        try (Connection conexao = provider.abrir(sessao.perfilConexao())) {
            conexao.setAutoCommit(false);
            try {
                boolean atualizado = dao.atualizar(conexao, id, dados.titulo(), dados.ano(),
                        dados.classificacao(), dados.idGenero());
                if (atualizado && administrador) {
                    dao.substituirPlataformas(conexao, id, dados.plataformas());
                }
                conexao.commit();
                return atualizado;
            } catch (SQLException | RuntimeException e) {
                rollback(conexao, e);
                throw e;
            }
        } catch (SQLException e) {
            throw traduzir(e, "atualizar o jogo", false);
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso aos jogos.", e);
        }
    }

    public boolean excluir(Sessao sessao, int id) {
        exigir(sessao);
        if (sessao.perfil() != PerfilFuncionario.ADMINISTRADOR) {
            throw new br.unoesc.pixelrent.exception.AcessoNegadoException(
                    "Somente administradores podem excluir jogos.");
        }
        validarId(id);
        try (Connection conexao = provider.abrir(sessao.perfilConexao())) {
            conexao.setAutoCommit(false);
            try {
                boolean excluido = dao.excluir(conexao, id);
                conexao.commit();
                return excluido;
            } catch (SQLException | RuntimeException e) {
                rollback(conexao, e);
                throw e;
            }
        } catch (SQLException e) {
            throw traduzir(e, "excluir o jogo", true);
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso aos jogos.", e);
        }
    }

    private void exigir(Sessao sessao) { politica.exigir(sessao, Permissao.JOGOS); }

    private static void validarId(int id) {
        if (id <= 0) throw new DadosInvalidosException("Identificador de jogo inválido.");
    }

    private static Dados validar(String titulo, Integer ano, String classificacao,
                                 Integer idGenero, List<Integer> plataformas,
                                 boolean exigirPlataformas) {
        if (titulo == null || titulo.isBlank()) throw new DadosInvalidosException("Informe o título do jogo.");
        String tituloOk = titulo.trim();
        if (tituloOk.length() > 150) throw new DadosInvalidosException("O título deve possuir no máximo 150 caracteres.");
        if (ano == null || ano < 1970 || ano > 2100) throw new DadosInvalidosException("Informe um ano entre 1970 e 2100.");
        String classificacaoOk = classificacao == null || classificacao.isBlank()
                ? "LIVRE" : classificacao.trim().toUpperCase();
        if (classificacaoOk.length() > 10) throw new DadosInvalidosException("A classificação indicativa deve possuir no máximo 10 caracteres.");
        if (idGenero == null || idGenero <= 0) throw new DadosInvalidosException("Selecione um gênero.");
        if (exigirPlataformas && (plataformas == null || plataformas.isEmpty())) {
            throw new DadosInvalidosException("Selecione ao menos uma plataforma.");
        }
        LinkedHashSet<Integer> unicas = new LinkedHashSet<>();
        if (plataformas != null) {
            for (Integer id : plataformas) {
                if (id == null || id <= 0) throw new DadosInvalidosException("Selecione plataformas válidas.");
                unicas.add(id);
            }
        }
        return new Dados(tituloOk, ano, classificacaoOk, idGenero, List.copyOf(unicas));
    }

    private static RuntimeException traduzir(SQLException e, String operacao, boolean exclusao) {
        if ("23503".equals(e.getSQLState())) {
            return new DadosInvalidosException(exclusao
                    ? "Não é possível excluir este jogo porque existem mídias ou registros associados."
                    : "O gênero ou uma das plataformas selecionadas não existe ou não está disponível.");
        }
        if ("23505".equals(e.getSQLState())) return new DadosInvalidosException("Já existe um registro com os dados informados.");
        return new FalhaTecnicaException("Não foi possível " + operacao + ".", e);
    }

    private static void rollback(Connection conexao, Throwable causa) {
        try { conexao.rollback(); } catch (SQLException erroRollback) { causa.addSuppressed(erroRollback); }
    }

    private record Dados(String titulo, int ano, String classificacao,
                         int idGenero, List<Integer> plataformas) { }
}
