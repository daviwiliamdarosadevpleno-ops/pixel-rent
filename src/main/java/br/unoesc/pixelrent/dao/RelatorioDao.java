package br.unoesc.pixelrent.dao;

import br.unoesc.pixelrent.model.FaturamentoMensal;
import br.unoesc.pixelrent.model.JogoMaisAlugado;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Consultas de leitura às views de relatórios já existentes no PostgreSQL. */
public final class RelatorioDao {

    private static final String SQL_JOGOS_MAIS_ALUGADOS = """
            SELECT titulo, genero, vezes_alugado
              FROM public.vw_jogos_mais_alugados
             ORDER BY vezes_alugado DESC, titulo, genero
            """;

    private static final String SQL_FATURAMENTO_MENSAL = """
            SELECT mes, qtd_locacoes, faturamento
              FROM public.vw_faturamento_mensal
             ORDER BY mes DESC
            """;

    public List<JogoMaisAlugado> listarJogosMaisAlugados(Connection conexao) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        try (PreparedStatement comando = conexao.prepareStatement(SQL_JOGOS_MAIS_ALUGADOS);
             ResultSet resultado = comando.executeQuery()) {
            List<JogoMaisAlugado> itens = new ArrayList<>();
            while (resultado.next()) {
                itens.add(new JogoMaisAlugado(
                        resultado.getString("titulo"),
                        resultado.getString("genero"),
                        resultado.getLong("vezes_alugado")));
            }
            return List.copyOf(itens);
        }
    }

    public List<FaturamentoMensal> listarFaturamentoMensal(Connection conexao) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        try (PreparedStatement comando = conexao.prepareStatement(SQL_FATURAMENTO_MENSAL);
             ResultSet resultado = comando.executeQuery()) {
            List<FaturamentoMensal> itens = new ArrayList<>();
            while (resultado.next()) {
                String mes = resultado.getString("mes");
                BigDecimal faturamento = resultado.getBigDecimal("faturamento");
                itens.add(new FaturamentoMensal(
                        YearMonth.parse(mes),
                        resultado.getLong("qtd_locacoes"),
                        faturamento == null ? BigDecimal.ZERO : faturamento));
            }
            return List.copyOf(itens);
        }
    }
}
