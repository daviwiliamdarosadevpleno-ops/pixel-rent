package br.unoesc.pixelrent.service;

import br.unoesc.pixelrent.dao.RelatorioDao;
import br.unoesc.pixelrent.db.ConnectionProvider;
import br.unoesc.pixelrent.exception.ConfiguracaoException;
import br.unoesc.pixelrent.exception.FalhaTecnicaException;
import br.unoesc.pixelrent.model.FaturamentoMensal;
import br.unoesc.pixelrent.model.JogoMaisAlugado;
import br.unoesc.pixelrent.security.Permissao;
import br.unoesc.pixelrent.security.PoliticaAcesso;
import br.unoesc.pixelrent.security.Sessao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/** Regras de acesso e consultas aos relatórios já existentes no banco. */
public final class RelatorioService {
    private final ConnectionProvider provider;
    private final RelatorioDao dao;
    private final PoliticaAcesso politica;

    public RelatorioService(ConnectionProvider provider, RelatorioDao dao, PoliticaAcesso politica) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.dao = Objects.requireNonNull(dao, "dao");
        this.politica = Objects.requireNonNull(politica, "politica");
    }

    public List<JogoMaisAlugado> listarJogosMaisAlugados(Sessao sessao) {
        politica.exigir(sessao, Permissao.RELATORIO_JOGOS_MAIS_ALUGADOS);
        try (Connection conexao = provider.abrirSomenteLeitura(sessao.perfilConexao())) {
            return dao.listarJogosMaisAlugados(conexao);
        } catch (SQLException | ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível consultar o relatório de jogos mais alugados.", e);
        }
    }

    public List<FaturamentoMensal> listarFaturamentoMensal(Sessao sessao) {
        politica.exigir(sessao, Permissao.RELATORIO_FATURAMENTO_MENSAL);
        try (Connection conexao = provider.abrirSomenteLeitura(sessao.perfilConexao())) {
            return dao.listarFaturamentoMensal(conexao);
        } catch (SQLException | ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível consultar o relatório de faturamento mensal.", e);
        }
    }
}
