package br.unoesc.pixelrent.dao;

import br.unoesc.pixelrent.model.Midia;
import br.unoesc.pixelrent.model.OpcaoJogoPlataforma;

import java.math.BigDecimal;
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
import java.util.Optional;

/** Acesso JDBC às mídias físicas e digitais. */
public final class MidiaDao {
    private static final String SELECT_BASE = """
            SELECT m.id_midia, m.id_jogo, j.titulo AS titulo_jogo,
                   m.id_plataforma, p.nome AS nome_plataforma,
                   m.tipo, m.status, m.valor_diaria,
                   mf.codigo_barras, mf.estado_conservacao,
                   md.codigo_licenca, md.validade
              FROM public.midia m
              JOIN public.jogo j ON j.id_jogo = m.id_jogo
              JOIN public.plataforma p ON p.id_plataforma = m.id_plataforma
              LEFT JOIN public.midia_fisica mf ON mf.id_midia = m.id_midia
              LEFT JOIN public.midia_digital md ON md.id_midia = m.id_midia
            """;

    private static final String SQL_LISTAR = SELECT_BASE + """
             WHERE (? = FALSE
                    OR j.titulo ILIKE ?
                    OR p.nome ILIKE ?
                    OR COALESCE(mf.codigo_barras, md.codigo_licenca, '') ILIKE ?)
             ORDER BY j.titulo, p.nome, m.id_midia
            """;

    private static final String SQL_BUSCAR = SELECT_BASE + " WHERE m.id_midia = ?";

    private static final String SQL_OPCOES = """
            SELECT jp.id_jogo, j.titulo, jp.id_plataforma, p.nome
              FROM public.jogo_plataforma jp
              JOIN public.jogo j ON j.id_jogo = jp.id_jogo
              JOIN public.plataforma p ON p.id_plataforma = jp.id_plataforma
             ORDER BY j.titulo, p.nome
            """;

    public List<Midia> listar(Connection conexao, String pesquisa) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        String termo = pesquisa == null || pesquisa.isBlank() ? null : "%" + pesquisa.trim() + "%";
        try (PreparedStatement comando = conexao.prepareStatement(SQL_LISTAR)) {
            comando.setBoolean(1, termo != null);
            if (termo == null) {
                comando.setNull(2, Types.VARCHAR);
                comando.setNull(3, Types.VARCHAR);
                comando.setNull(4, Types.VARCHAR);
            } else {
                comando.setString(2, termo);
                comando.setString(3, termo);
                comando.setString(4, termo);
            }
            try (ResultSet resultado = comando.executeQuery()) {
                List<Midia> itens = new ArrayList<>();
                while (resultado.next()) itens.add(mapear(resultado));
                return List.copyOf(itens);
            }
        }
    }

    public Optional<Midia> buscarPorId(Connection conexao, int id) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        try (PreparedStatement comando = conexao.prepareStatement(SQL_BUSCAR)) {
            comando.setInt(1, id);
            try (ResultSet resultado = comando.executeQuery()) {
                return resultado.next() ? Optional.of(mapear(resultado)) : Optional.empty();
            }
        }
    }

    public List<OpcaoJogoPlataforma> listarOpcoes(Connection conexao) throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        try (PreparedStatement comando = conexao.prepareStatement(SQL_OPCOES);
             ResultSet resultado = comando.executeQuery()) {
            List<OpcaoJogoPlataforma> opcoes = new ArrayList<>();
            while (resultado.next()) {
                opcoes.add(new OpcaoJogoPlataforma(
                        resultado.getInt("id_jogo"), resultado.getString("titulo"),
                        resultado.getInt("id_plataforma"), resultado.getString("nome")));
            }
            return List.copyOf(opcoes);
        }
    }

    public int inserirBase(Connection conexao, int idJogo, int idPlataforma,
                           String tipo, BigDecimal valorDiaria) throws SQLException {
        String sql = """
                INSERT INTO public.midia (id_jogo, id_plataforma, tipo, status, valor_diaria)
                VALUES (?, ?, ?, 'DISPONIVEL', ?)
                RETURNING id_midia
                """;
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setInt(1, idJogo);
            comando.setInt(2, idPlataforma);
            comando.setString(3, tipo);
            comando.setBigDecimal(4, valorDiaria);
            try (ResultSet resultado = comando.executeQuery()) {
                if (!resultado.next()) throw new SQLException("O banco não retornou a mídia cadastrada.");
                return resultado.getInt("id_midia");
            }
        }
    }

    public void inserirFisica(Connection conexao, int idMidia,
                              String codigoBarras, String estado) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement("""
                INSERT INTO public.midia_fisica (id_midia, codigo_barras, estado_conservacao, tipo)
                VALUES (?, ?, ?, 'FISICA')
                """)) {
            comando.setInt(1, idMidia);
            comando.setString(2, codigoBarras);
            comando.setString(3, estado);
            comando.executeUpdate();
        }
    }

    public void inserirDigital(Connection conexao, int idMidia,
                               String codigoLicenca, LocalDate validade) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement("""
                INSERT INTO public.midia_digital (id_midia, codigo_licenca, validade, tipo)
                VALUES (?, ?, ?, 'DIGITAL')
                """)) {
            comando.setInt(1, idMidia);
            comando.setString(2, codigoLicenca);
            comando.setDate(3, Date.valueOf(validade));
            comando.executeUpdate();
        }
    }

    /** Atualiza somente dados editáveis; tipo, jogo, plataforma e status preservam o histórico. */
    public boolean atualizarBaseDisponivel(Connection conexao, int id,
                                           BigDecimal valorDiaria) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement("""
                UPDATE public.midia
                   SET valor_diaria = ?
                 WHERE id_midia = ? AND status = 'DISPONIVEL'
                """)) {
            comando.setBigDecimal(1, valorDiaria);
            comando.setInt(2, id);
            return comando.executeUpdate() > 0;
        }
    }

    public void atualizarFisica(Connection conexao, int id,
                                String codigoBarras, String estado) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement("""
                UPDATE public.midia_fisica
                   SET codigo_barras = ?, estado_conservacao = ?
                 WHERE id_midia = ?
                """)) {
            comando.setString(1, codigoBarras);
            comando.setString(2, estado);
            comando.setInt(3, id);
            if (comando.executeUpdate() == 0) throw new SQLException("Cadastro físico especializado não encontrado.");
        }
    }

    public void atualizarDigital(Connection conexao, int id,
                                 String codigoLicenca, LocalDate validade) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement("""
                UPDATE public.midia_digital
                   SET codigo_licenca = ?, validade = ?
                 WHERE id_midia = ?
                """)) {
            comando.setString(1, codigoLicenca);
            comando.setDate(2, Date.valueOf(validade));
            comando.setInt(3, id);
            if (comando.executeUpdate() == 0) throw new SQLException("Cadastro digital especializado não encontrado.");
        }
    }

    /** Só remove mídias disponíveis; o histórico de locações é preservado pela FK. */
    public boolean excluirDisponivel(Connection conexao, int id) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(
                "DELETE FROM public.midia WHERE id_midia = ? AND status = 'DISPONIVEL'")) {
            comando.setInt(1, id);
            return comando.executeUpdate() > 0;
        }
    }

    private static Midia mapear(ResultSet resultado) throws SQLException {
        Date validade = resultado.getDate("validade");
        return new Midia(
                resultado.getInt("id_midia"),
                resultado.getInt("id_jogo"),
                resultado.getString("titulo_jogo"),
                resultado.getInt("id_plataforma"),
                resultado.getString("nome_plataforma"),
                resultado.getString("tipo"),
                resultado.getString("status"),
                resultado.getBigDecimal("valor_diaria"),
                resultado.getString("codigo_barras"),
                resultado.getString("estado_conservacao"),
                resultado.getString("codigo_licenca"),
                validade == null ? null : validade.toLocalDate());
    }
}
