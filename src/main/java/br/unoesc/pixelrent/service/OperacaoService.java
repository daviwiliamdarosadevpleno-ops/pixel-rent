package br.unoesc.pixelrent.service;

import br.unoesc.pixelrent.dao.OperacaoDao;
import br.unoesc.pixelrent.db.ConnectionProvider;
import br.unoesc.pixelrent.exception.AcessoNegadoException;
import br.unoesc.pixelrent.exception.ConfiguracaoException;
import br.unoesc.pixelrent.exception.DadosInvalidosException;
import br.unoesc.pixelrent.exception.FalhaTecnicaException;
import br.unoesc.pixelrent.model.ClienteOpcaoLocacao;
import br.unoesc.pixelrent.model.ItemHistoricoLocacao;
import br.unoesc.pixelrent.model.ItemLocacaoAtiva;
import br.unoesc.pixelrent.model.LocacaoAtrasada;
import br.unoesc.pixelrent.model.MidiaManutencao;
import br.unoesc.pixelrent.model.MidiaOpcaoLocacao;
import br.unoesc.pixelrent.model.PainelConsultas;
import br.unoesc.pixelrent.security.Permissao;
import br.unoesc.pixelrent.security.PoliticaAcesso;
import br.unoesc.pixelrent.security.Sessao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Regras de autorização e coordenação das operações existentes no banco. */
public final class OperacaoService {
    private final ConnectionProvider provider;
    private final OperacaoDao dao;
    private final PoliticaAcesso politica;

    public OperacaoService(ConnectionProvider provider, OperacaoDao dao, PoliticaAcesso politica) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.dao = Objects.requireNonNull(dao, "dao");
        this.politica = Objects.requireNonNull(politica, "politica");
    }

    public List<ItemLocacaoAtiva> listarLocacoesAtivas(Sessao sessao) {
        exigir(sessao, Permissao.LOCACOES);
        try (Connection c = provider.abrirSomenteLeitura(sessao.perfilConexao())) {
            return dao.listarLocacoesAtivas(c);
        } catch (SQLException e) {
            throw traduzir(e, "consultar as locações");
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso às locações.", e);
        }
    }

    public List<ClienteOpcaoLocacao> listarClientesAtivos(Sessao sessao) {
        exigir(sessao, Permissao.LOCACOES);
        try (Connection c = provider.abrirSomenteLeitura(sessao.perfilConexao())) {
            return dao.listarClientesAtivos(c);
        } catch (SQLException e) {
            throw traduzir(e, "consultar os clientes ativos");
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso aos clientes.", e);
        }
    }

    public List<MidiaOpcaoLocacao> listarMidiasDisponiveis(Sessao sessao) {
        exigir(sessao, Permissao.LOCACOES);
        try (Connection c = provider.abrirSomenteLeitura(sessao.perfilConexao())) {
            return dao.listarMidiasDisponiveis(c);
        } catch (SQLException e) {
            throw traduzir(e, "consultar as mídias disponíveis");
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso às mídias.", e);
        }
    }

    public int criarLocacao(Sessao sessao, Integer idCliente, List<Integer> idsMidias, Integer dias) {
        exigir(sessao, Permissao.LOCACOES);
        if (idCliente == null || idCliente <= 0) throw new DadosInvalidosException("Selecione um cliente.");
        if (dias == null || dias <= 0) throw new DadosInvalidosException("Informe uma quantidade de dias maior que zero.");
        if (idsMidias == null || idsMidias.isEmpty()) throw new DadosInvalidosException("Selecione ao menos uma mídia.");
        LinkedHashSet<Integer> unicos = new LinkedHashSet<>();
        for (Integer id : idsMidias) {
            if (id == null || id <= 0) throw new DadosInvalidosException("A seleção contém uma mídia inválida.");
            unicos.add(id);
        }
        if (unicos.size() != idsMidias.size()) throw new DadosInvalidosException("Não selecione a mesma mídia mais de uma vez.");
        try (Connection c = provider.abrir(sessao.perfilConexao())) {
            return dao.realizarLocacao(c, idCliente, sessao.idFuncionario(), List.copyOf(unicos), dias);
        } catch (SQLException e) {
            throw traduzir(e, "registrar a locação");
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso às locações.", e);
        }
    }

    public List<ItemLocacaoAtiva> listarItensPendentes(Sessao sessao) {
        exigir(sessao, Permissao.DEVOLUCOES);
        try (Connection c = provider.abrirSomenteLeitura(sessao.perfilConexao())) {
            return dao.listarItensPendentes(c);
        } catch (SQLException e) {
            throw traduzir(e, "consultar mídias pendentes de devolução");
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso às devoluções.", e);
        }
    }

    public BigDecimal registrarDevolucao(Sessao sessao, int idLocacao, int idMidia, boolean comDefeito) {
        exigir(sessao, Permissao.DEVOLUCOES);
        if (idLocacao <= 0 || idMidia <= 0) throw new DadosInvalidosException("Identificador da devolução inválido.");
        try (Connection c = provider.abrir(sessao.perfilConexao())) {
            return dao.registrarDevolucao(c, idLocacao, idMidia, comDefeito, sessao.idFuncionario());
        } catch (SQLException e) {
            throw traduzir(e, "registrar a devolução");
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso às devoluções.", e);
        }
    }

    public List<MidiaManutencao> listarManutencao(Sessao sessao) {
        exigir(sessao, Permissao.MANUTENCAO);
        try (Connection c = provider.abrirSomenteLeitura(sessao.perfilConexao())) {
            return dao.listarMidiasManutencao(c);
        } catch (SQLException e) {
            throw traduzir(e, "consultar mídias em manutenção");
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso à manutenção.", e);
        }
    }

    public boolean liberarManutencao(Sessao sessao, int idMidia) {
        exigir(sessao, Permissao.MANUTENCAO);
        if (idMidia <= 0) throw new DadosInvalidosException("Identificador de mídia inválido.");
        try (Connection c = provider.abrir(sessao.perfilConexao())) {
            return dao.liberarDaManutencao(c, idMidia);
        } catch (SQLException e) {
            throw traduzir(e, "liberar a mídia da manutenção");
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso à manutenção.", e);
        }
    }

    public PainelConsultas consultarPainel(Sessao sessao) {
        exigir(sessao, Permissao.CONSULTAS);
        try (Connection c = provider.abrirSomenteLeitura(sessao.perfilConexao())) {
            List<ItemLocacaoAtiva> ativas = dao.listarLocacoesAtivas(c);
            List<LocacaoAtrasada> atrasadas = dao.listarAtrasadas(c);
            List<ItemHistoricoLocacao> historico = dao.listarHistorico(c);
            int disponiveis = dao.contarMidiasDisponiveis(c);
            return new PainelConsultas(ativas, atrasadas, historico, disponiveis);
        } catch (SQLException e) {
            throw traduzir(e, "consultar os indicadores e históricos");
        } catch (ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível configurar o acesso às consultas.", e);
        }
    }

    private void exigir(Sessao sessao, Permissao permissao) {
        politica.exigir(sessao, permissao);
    }

    private static RuntimeException traduzir(SQLException e, String operacao) {
        String mensagem = mensagemCompleta(e).toLowerCase();
        String estado = e.getSQLState();
        if ("P0001".equals(estado)) {
            if (mensagem.contains("cliente") && mensagem.contains("inativo"))
                return new DadosInvalidosException("O cliente selecionado está inativo.");
            if (mensagem.contains("cliente") && mensagem.contains("nao encontrado"))
                return new DadosInvalidosException("O cliente selecionado não foi encontrado.");
            if (mensagem.contains("funcionario") && mensagem.contains("inativo"))
                return new DadosInvalidosException("Seu usuário está inativo e não pode realizar a operação.");
            if (mensagem.contains("funcionario") && mensagem.contains("nao encontrado"))
                return new DadosInvalidosException("O funcionário da sessão não foi encontrado.");
            if (mensagem.contains("ja foi devolvida"))
                return new DadosInvalidosException("Esta mídia já foi devolvida.");
            if (mensagem.contains("item nao encontrado") || mensagem.contains("locacao") && mensagem.contains("nao encontrada"))
                return new DadosInvalidosException("A locação ou o item selecionado não foi encontrado.");
            if (mensagem.contains("licenca") && mensagem.contains("vencida"))
                return new DadosInvalidosException("Uma das licenças digitais está vencida.");
            if (mensagem.contains("nao esta disponivel") || mensagem.contains("lista de midias invalida"))
                return new DadosInvalidosException("Uma das mídias não está disponível ou a seleção é inválida.");
            if (mensagem.contains("quantidade de dias invalida"))
                return new DadosInvalidosException("Informe uma quantidade de dias maior que zero.");
            if (mensagem.contains("informe ao menos uma midia"))
                return new DadosInvalidosException("Selecione ao menos uma mídia.");
            if (mensagem.contains("nao possui cadastro especializado") || mensagem.contains("nao possui cadastro de licenca"))
                return new DadosInvalidosException("Uma das mídias possui cadastro especializado incompleto.");
            return new DadosInvalidosException("O banco recusou a operação. Confira cliente, mídias e disponibilidade.");
        }
        if ("23503".equals(estado))
            return new DadosInvalidosException("Um dos registros relacionados não existe mais.");
        if ("23514".equals(estado))
            return new DadosInvalidosException("Um dos valores informados não atende às regras do banco.");
        return new FalhaTecnicaException("Não foi possível " + operacao + ".", e);
    }

    private static String mensagemCompleta(SQLException e) {
        StringBuilder sb = new StringBuilder();
        for (SQLException atual = e; atual != null; atual = atual.getNextException()) {
            if (atual.getMessage() != null) sb.append(' ').append(atual.getMessage());
        }
        return sb.toString();
    }
}
