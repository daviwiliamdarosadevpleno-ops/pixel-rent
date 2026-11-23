package br.unoesc.pixelrent.service;

import br.unoesc.pixelrent.dao.GeneroDao;
import br.unoesc.pixelrent.db.ConnectionProvider;
import br.unoesc.pixelrent.exception.ConfiguracaoException;
import br.unoesc.pixelrent.exception.DadosInvalidosException;
import br.unoesc.pixelrent.exception.FalhaTecnicaException;
import br.unoesc.pixelrent.model.RegistroCatalogo;
import br.unoesc.pixelrent.security.Permissao;
import br.unoesc.pixelrent.security.PoliticaAcesso;
import br.unoesc.pixelrent.security.Sessao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Regras de negócio e autorização do cadastro de gêneros. */
public final class GeneroService {

    private final ConnectionProvider provider;
    private final GeneroDao dao;
    private final PoliticaAcesso politica;

    public GeneroService(ConnectionProvider provider, GeneroDao dao, PoliticaAcesso politica) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.dao = Objects.requireNonNull(dao, "dao");
        this.politica = Objects.requireNonNull(politica, "politica");
    }

    public List<RegistroCatalogo> listar(Sessao sessao, String pesquisa) {
        exigirPermissao(sessao);
        try (Connection conexao = provider.abrirSomenteLeitura(sessao.perfilConexao())) {
            return dao.listar(conexao, normalizarPesquisa(pesquisa));
        } catch (SQLException e) {
            throw traduzir(e, "consultar gêneros", false);
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso aos gêneros.", e);
        }
    }

    public Optional<RegistroCatalogo> buscarPorId(Sessao sessao, int id) {
        exigirPermissao(sessao);
        validarId(id);
        try (Connection conexao = provider.abrirSomenteLeitura(sessao.perfilConexao())) {
            return dao.buscarPorId(conexao, id);
        } catch (SQLException e) {
            throw traduzir(e, "consultar o gênero", false);
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso aos gêneros.", e);
        }
    }

    public RegistroCatalogo cadastrar(Sessao sessao, String nome) {
        exigirPermissao(sessao);
        String validado = validarNome(nome);
        try (Connection conexao = provider.abrir(sessao.perfilConexao())) {
            return dao.inserir(conexao, validado);
        } catch (SQLException e) {
            throw traduzir(e, "cadastrar o gênero", false);
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso aos gêneros.", e);
        }
    }

    public Optional<RegistroCatalogo> atualizar(Sessao sessao, int id, String nome) {
        exigirPermissao(sessao);
        validarId(id);
        String validado = validarNome(nome);
        try (Connection conexao = provider.abrir(sessao.perfilConexao())) {
            return dao.atualizar(conexao, id, validado);
        } catch (SQLException e) {
            throw traduzir(e, "atualizar o gênero", false);
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso aos gêneros.", e);
        }
    }

    public boolean excluir(Sessao sessao, int id) {
        exigirPermissao(sessao);
        validarId(id);
        try (Connection conexao = provider.abrir(sessao.perfilConexao())) {
            return dao.excluir(conexao, id);
        } catch (SQLException e) {
            throw traduzir(e, "excluir o gênero", true);
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso aos gêneros.", e);
        }
    }

    private void exigirPermissao(Sessao sessao) {
        politica.exigir(sessao, Permissao.GENEROS);
    }

    private static void validarId(int id) {
        if (id <= 0) throw new DadosInvalidosException("Identificador de gênero inválido.");
    }

    private static String validarNome(String nome) {
        if (nome == null || nome.isBlank()) throw new DadosInvalidosException("Informe o nome do gênero.");
        String valor = nome.trim();
        if (valor.length() > 50) throw new DadosInvalidosException("O nome do gênero deve possuir no máximo 50 caracteres.");
        return valor;
    }

    private static String normalizarPesquisa(String pesquisa) {
        return pesquisa == null || pesquisa.isBlank() ? null : pesquisa.trim();
    }

    private static RuntimeException traduzir(SQLException erro, String operacao, boolean exclusao) {
        if ("23505".equals(erro.getSQLState())) {
            return new DadosInvalidosException("Já existe um gênero com esse nome.");
        }
        if (exclusao && "23503".equals(erro.getSQLState())) {
            return new DadosInvalidosException("Não é possível excluir este gênero porque ele está associado a um ou mais jogos.");
        }
        return new FalhaTecnicaException("Não foi possível " + operacao + ".", erro);
    }
}
