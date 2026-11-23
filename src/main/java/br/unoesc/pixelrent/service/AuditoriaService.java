package br.unoesc.pixelrent.service;

import br.unoesc.pixelrent.dao.AuditoriaDao;
import br.unoesc.pixelrent.db.ConnectionProvider;
import br.unoesc.pixelrent.exception.ConfiguracaoException;
import br.unoesc.pixelrent.exception.DadosInvalidosException;
import br.unoesc.pixelrent.exception.FalhaTecnicaException;
import br.unoesc.pixelrent.model.RegistroAuditoria;
import br.unoesc.pixelrent.security.Permissao;
import br.unoesc.pixelrent.security.PoliticaAcesso;
import br.unoesc.pixelrent.security.Sessao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Consulta somente leitura dos registros de auditoria de locações. */
public final class AuditoriaService {
    private static final Set<String> OPERACOES = Set.of("INSERT", "UPDATE", "DELETE");
    private final ConnectionProvider provider;
    private final AuditoriaDao dao;
    private final PoliticaAcesso politica;

    public AuditoriaService(ConnectionProvider provider, AuditoriaDao dao, PoliticaAcesso politica) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.dao = Objects.requireNonNull(dao, "dao");
        this.politica = Objects.requireNonNull(politica, "politica");
    }

    public List<RegistroAuditoria> listar(Sessao sessao, Integer idLocacao, String operacao) {
        politica.exigir(sessao, Permissao.AUDITORIA);
        if (idLocacao != null && idLocacao <= 0) {
            throw new DadosInvalidosException("Informe um número de locação válido.");
        }
        String filtro = operacao == null || operacao.isBlank() ? null : operacao.trim().toUpperCase(Locale.ROOT);
        if (filtro != null && !OPERACOES.contains(filtro)) {
            throw new DadosInvalidosException("Selecione uma operação de auditoria válida.");
        }
        try (Connection conexao = provider.abrirSomenteLeitura(sessao.perfilConexao())) {
            return dao.listar(conexao, idLocacao, filtro);
        } catch (SQLException | ConfiguracaoException e) {
            throw new FalhaTecnicaException("Não foi possível consultar a auditoria.", e);
        }
    }
}
