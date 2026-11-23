package br.unoesc.pixelrent.dao;

import br.unoesc.pixelrent.model.ClienteOpcaoLocacao;
import br.unoesc.pixelrent.model.ItemHistoricoLocacao;
import br.unoesc.pixelrent.model.ItemLocacaoAtiva;
import br.unoesc.pixelrent.model.LocacaoAtrasada;
import br.unoesc.pixelrent.model.MidiaManutencao;
import br.unoesc.pixelrent.model.MidiaOpcaoLocacao;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Consultas operacionais e chamadas às procedures já existentes no PostgreSQL. */
public final class OperacaoDao {

    private static final String SQL_CLIENTES_ATIVOS = """
            SELECT id_cliente, nome
              FROM public.cliente
             WHERE ativo = TRUE
             ORDER BY nome, id_cliente
            """;

    /* Replica os critérios da vw_catalogo_disponivel usando tabelas com SELECT concedido aos perfis. */
    private static final String SQL_MIDIAS_DISPONIVEIS = """
            SELECT m.id_midia, j.titulo, g.nome AS genero, p.nome AS plataforma,
                   m.tipo, COALESCE(mf.codigo_barras, md.codigo_licenca) AS identificador,
                   m.valor_diaria
              FROM public.midia m
              JOIN public.jogo j ON j.id_jogo = m.id_jogo
              JOIN public.genero g ON g.id_genero = j.id_genero
              JOIN public.plataforma p ON p.id_plataforma = m.id_plataforma
              LEFT JOIN public.midia_fisica mf ON mf.id_midia = m.id_midia
              LEFT JOIN public.midia_digital md ON md.id_midia = m.id_midia
             WHERE m.status = 'DISPONIVEL'
               AND (
                    (m.tipo = 'FISICA' AND mf.id_midia IS NOT NULL AND md.id_midia IS NULL)
                    OR
                    (m.tipo = 'DIGITAL' AND md.id_midia IS NOT NULL AND mf.id_midia IS NULL
                     AND (md.validade IS NULL OR md.validade >= CURRENT_DATE))
               )
             ORDER BY j.titulo, p.nome, m.id_midia
            """;

    private static final String SQL_LOCACOES_ATIVAS = """
            SELECT id_locacao, cliente, funcionario, titulo, plataforma,
                   data_locacao, prazo_devolucao, status,
                   id_item_locacao, id_midia, valor_diaria
              FROM public.vw_locacoes_ativas
             ORDER BY prazo_devolucao, id_locacao, id_item_locacao
            """;

    private static final String SQL_HISTORICO = """
            SELECT id_cliente, cliente, id_locacao, titulo, plataforma,
                   data_locacao, data_devolucao, valor_diaria, multa, status
              FROM public.vw_historico_cliente
             ORDER BY data_locacao DESC, id_locacao DESC, titulo
            """;

    private static final String SQL_ATRASADAS = """
            SELECT id_locacao, cliente, telefone, titulo, prazo_devolucao,
                   dias_atraso, multa_estimada
              FROM public.vw_locacoes_atrasadas
             ORDER BY prazo_devolucao, id_locacao, titulo
            """;

    private static final String SQL_MANUTENCAO = """
            SELECT m.id_midia, j.titulo AS titulo_jogo, p.nome AS plataforma,
                   m.tipo, COALESCE(mf.codigo_barras, md.codigo_licenca) AS identificador,
                   md.validade
              FROM public.midia m
              JOIN public.jogo j ON j.id_jogo = m.id_jogo
              JOIN public.plataforma p ON p.id_plataforma = m.id_plataforma
              LEFT JOIN public.midia_fisica mf ON mf.id_midia = m.id_midia
              LEFT JOIN public.midia_digital md ON md.id_midia = m.id_midia
             WHERE m.status = 'MANUTENCAO'
             ORDER BY j.titulo, p.nome, m.id_midia
            """;

    public List<ClienteOpcaoLocacao> listarClientesAtivos(Connection conexao) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        try (PreparedStatement ps = conexao.prepareStatement(SQL_CLIENTES_ATIVOS);
             ResultSet rs = ps.executeQuery()) {
            List<ClienteOpcaoLocacao> itens = new ArrayList<>();
            while (rs.next()) itens.add(new ClienteOpcaoLocacao(rs.getInt("id_cliente"), rs.getString("nome")));
            return List.copyOf(itens);
        }
    }

    public List<MidiaOpcaoLocacao> listarMidiasDisponiveis(Connection conexao) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        try (PreparedStatement ps = conexao.prepareStatement(SQL_MIDIAS_DISPONIVEIS);
             ResultSet rs = ps.executeQuery()) {
            List<MidiaOpcaoLocacao> itens = new ArrayList<>();
            while (rs.next()) {
                itens.add(new MidiaOpcaoLocacao(
                        rs.getInt("id_midia"), rs.getString("titulo"), rs.getString("genero"),
                        rs.getString("plataforma"), rs.getString("tipo"),
                        rs.getString("identificador"), rs.getBigDecimal("valor_diaria")));
            }
            return List.copyOf(itens);
        }
    }

    /** Chama a procedure SECURITY DEFINER e registra o funcionário responsável na auditoria. */
    public int realizarLocacao(Connection conexao, int idCliente, int idFuncionario,
                               List<Integer> idsMidias, int dias) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        return executarComContextoAuditoria(conexao, idFuncionario, () -> {
            Array array = conexao.createArrayOf("integer", idsMidias.toArray(Integer[]::new));
            try (PreparedStatement ps = conexao.prepareStatement(
                    "CALL public.sp_realizar_locacao(?, ?, ?, ?, ?)")) {
                ps.setInt(1, idCliente);
                ps.setInt(2, idFuncionario);
                ps.setArray(3, array);
                ps.setInt(4, dias);
                ps.setNull(5, Types.INTEGER);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SQLException("A procedure não retornou o identificador da locação.");
                    int id = rs.getInt(1);
                    if (rs.wasNull() || id <= 0) {
                        throw new SQLException("A procedure retornou um identificador de locação inválido.");
                    }
                    return id;
                }
            } finally {
                array.free();
            }
        });
    }

    public List<ItemLocacaoAtiva> listarLocacoesAtivas(Connection conexao) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        try (PreparedStatement ps = conexao.prepareStatement(SQL_LOCACOES_ATIVAS);
             ResultSet rs = ps.executeQuery()) {
            List<ItemLocacaoAtiva> itens = new ArrayList<>();
            while (rs.next()) itens.add(mapearAtiva(rs));
            return List.copyOf(itens);
        }
    }

    /** Cada linha representa uma mídia ainda pendente na locação e pode ser devolvida individualmente. */
    public List<ItemLocacaoAtiva> listarItensPendentes(Connection conexao) throws SQLException {
        return listarLocacoesAtivas(conexao);
    }

    public BigDecimal registrarDevolucao(Connection conexao, int idLocacao,
                                         int idMidia, boolean comDefeito,
                                         int idFuncionario) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        return executarComContextoAuditoria(conexao, idFuncionario, () -> {
            try (PreparedStatement ps = conexao.prepareStatement(
                    "CALL public.sp_registrar_devolucao(?, ?, ?, ?)")) {
                ps.setInt(1, idLocacao);
                ps.setInt(2, idMidia);
                ps.setBoolean(3, comDefeito);
                ps.setBigDecimal(4, BigDecimal.ZERO);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SQLException("A procedure não retornou a multa calculada.");
                    BigDecimal multa = rs.getBigDecimal(1);
                    return multa == null ? BigDecimal.ZERO : multa;
                }
            }
        });
    }

    public List<LocacaoAtrasada> listarAtrasadas(Connection conexao) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        try (PreparedStatement ps = conexao.prepareStatement(SQL_ATRASADAS);
             ResultSet rs = ps.executeQuery()) {
            List<LocacaoAtrasada> itens = new ArrayList<>();
            while (rs.next()) {
                itens.add(new LocacaoAtrasada(rs.getInt("id_locacao"), rs.getString("cliente"),
                        rs.getString("telefone"), rs.getString("titulo"),
                        rs.getDate("prazo_devolucao").toLocalDate(), rs.getInt("dias_atraso"),
                        rs.getBigDecimal("multa_estimada")));
            }
            return List.copyOf(itens);
        }
    }

    public List<ItemHistoricoLocacao> listarHistorico(Connection conexao) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        try (PreparedStatement ps = conexao.prepareStatement(SQL_HISTORICO);
             ResultSet rs = ps.executeQuery()) {
            List<ItemHistoricoLocacao> itens = new ArrayList<>();
            while (rs.next()) {
                Date devolucao = rs.getDate("data_devolucao");
                itens.add(new ItemHistoricoLocacao(
                        rs.getInt("id_cliente"), rs.getString("cliente"), rs.getInt("id_locacao"),
                        rs.getString("titulo"), rs.getString("plataforma"),
                        rs.getDate("data_locacao").toLocalDate(),
                        devolucao == null ? null : devolucao.toLocalDate(),
                        rs.getBigDecimal("valor_diaria"), rs.getBigDecimal("multa"), rs.getString("status")));
            }
            return List.copyOf(itens);
        }
    }

    public List<MidiaManutencao> listarMidiasManutencao(Connection conexao) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        try (PreparedStatement ps = conexao.prepareStatement(SQL_MANUTENCAO);
             ResultSet rs = ps.executeQuery()) {
            List<MidiaManutencao> itens = new ArrayList<>();
            while (rs.next()) {
                Date validade = rs.getDate("validade");
                itens.add(new MidiaManutencao(rs.getInt("id_midia"), rs.getString("titulo_jogo"),
                        rs.getString("plataforma"), rs.getString("tipo"), rs.getString("identificador"),
                        validade == null ? null : validade.toLocalDate()));
            }
            return List.copyOf(itens);
        }
    }

    /** Libera a mídia após o reparo; a view de catálogo continua filtrando licenças digitais vencidas. */
    public boolean liberarDaManutencao(Connection conexao, int idMidia) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        try (PreparedStatement ps = conexao.prepareStatement("""
                UPDATE public.midia
                   SET status = 'DISPONIVEL'
                 WHERE id_midia = ?
                   AND status = 'MANUTENCAO'
                """)) {
            ps.setInt(1, idMidia);
            return ps.executeUpdate() > 0;
        }
    }

    public int contarMidiasDisponiveis(Connection conexao) throws SQLException {
        try (PreparedStatement ps = conexao.prepareStatement("""
                SELECT COUNT(*)
                  FROM public.midia m
                  LEFT JOIN public.midia_fisica mf ON mf.id_midia = m.id_midia
                  LEFT JOIN public.midia_digital md ON md.id_midia = m.id_midia
                 WHERE m.status = 'DISPONIVEL'
                   AND (
                        (m.tipo = 'FISICA' AND mf.id_midia IS NOT NULL AND md.id_midia IS NULL)
                        OR
                        (m.tipo = 'DIGITAL' AND md.id_midia IS NOT NULL AND mf.id_midia IS NULL
                         AND (md.validade IS NULL OR md.validade >= CURRENT_DATE))
                   )
                """);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /**
     * Define app.id_funcionario na transação antes da procedure, para a trigger de auditoria
     * identificar a pessoa autenticada. Em conexões recém-abertas, este método também controla
     * commit/rollback; se a conexão já estiver em transação, respeita o controle do chamador.
     */
    private static <T> T executarComContextoAuditoria(
            Connection conexao, int idFuncionario, OperacaoTransacional<T> operacao) throws SQLException {
        if (idFuncionario <= 0) throw new SQLException("Identificador do funcionário inválido para auditoria.");
        boolean controlaTransacao = conexao.getAutoCommit();
        if (controlaTransacao) conexao.setAutoCommit(false);
        try {
            try (PreparedStatement contexto = conexao.prepareStatement(
                    "SELECT pg_catalog.set_config('app.id_funcionario', ?, TRUE)")) {
                contexto.setString(1, Integer.toString(idFuncionario));
                try (ResultSet resultado = contexto.executeQuery()) {
                    if (!resultado.next()) throw new SQLException("Não foi possível definir o contexto da auditoria.");
                }
            }
            T retorno = operacao.executar();
            if (controlaTransacao) conexao.commit();
            return retorno;
        } catch (SQLException | RuntimeException erro) {
            if (controlaTransacao) {
                try {
                    conexao.rollback();
                } catch (SQLException rollbackErro) {
                    erro.addSuppressed(rollbackErro);
                }
            }
            throw erro;
        } finally {
            if (controlaTransacao) conexao.setAutoCommit(true);
        }
    }

    @FunctionalInterface
    private interface OperacaoTransacional<T> {
        T executar() throws SQLException;
    }

    private static ItemLocacaoAtiva mapearAtiva(ResultSet rs) throws SQLException {
        return new ItemLocacaoAtiva(
                rs.getInt("id_locacao"), rs.getInt("id_item_locacao"), rs.getInt("id_midia"),
                rs.getString("cliente"), rs.getString("funcionario"), rs.getString("titulo"),
                rs.getString("plataforma"), rs.getDate("data_locacao").toLocalDate(),
                rs.getDate("prazo_devolucao").toLocalDate(), rs.getString("status"),
                rs.getBigDecimal("valor_diaria"));
    }
}
