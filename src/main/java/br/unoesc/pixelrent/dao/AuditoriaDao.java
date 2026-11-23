package br.unoesc.pixelrent.dao;

import br.unoesc.pixelrent.model.RegistroAuditoria;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Leitura restrita do histórico de auditoria das alterações em locacao. */
public final class AuditoriaDao {

    private static final String SQL_LISTAR = """
            SELECT a.id_auditoria, a.id_locacao, a.operacao, a.usuario_bd,
                   a.data_hora, a.id_funcionario_acao, f.nome AS nome_funcionario,
                   a.dados_antigos::text AS dados_antigos_json,
                   a.dados_novos::text AS dados_novos_json
              FROM public.auditoria_locacao a
              LEFT JOIN public.funcionario f
                     ON f.id_funcionario = a.id_funcionario_acao
             WHERE (? IS NULL OR a.id_locacao = ?)
               AND (? IS NULL OR a.operacao = ?)
             ORDER BY a.data_hora DESC, a.id_auditoria DESC
             LIMIT 250
            """;

    public List<RegistroAuditoria> listar(Connection conexao, Integer idLocacao, String operacao)
            throws SQLException {
        Objects.requireNonNull(conexao, "conexao");
        try (PreparedStatement comando = conexao.prepareStatement(SQL_LISTAR)) {
            if (idLocacao == null) {
                comando.setNull(1, Types.INTEGER);
                comando.setNull(2, Types.INTEGER);
            } else {
                comando.setInt(1, idLocacao);
                comando.setInt(2, idLocacao);
            }
            if (operacao == null) {
                comando.setNull(3, Types.VARCHAR);
                comando.setNull(4, Types.VARCHAR);
            } else {
                comando.setString(3, operacao);
                comando.setString(4, operacao);
            }

            try (ResultSet resultado = comando.executeQuery()) {
                List<RegistroAuditoria> itens = new ArrayList<>();
                while (resultado.next()) {
                    Timestamp dataHora = resultado.getTimestamp("data_hora");
                    Integer idFuncionario = resultado.getObject("id_funcionario_acao", Integer.class);
                    itens.add(new RegistroAuditoria(
                            resultado.getInt("id_auditoria"),
                            resultado.getInt("id_locacao"),
                            resultado.getString("operacao"),
                            resultado.getString("usuario_bd"),
                            dataHora == null ? null : dataHora.toLocalDateTime(),
                            idFuncionario,
                            resultado.getString("nome_funcionario"),
                            resultado.getString("dados_antigos_json"),
                            resultado.getString("dados_novos_json")));
                }
                return List.copyOf(itens);
            }
        }
    }
}
