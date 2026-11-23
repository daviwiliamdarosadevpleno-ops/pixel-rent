package br.unoesc.pixelrent.model;

import java.time.LocalDateTime;

/** Registro somente de leitura da auditoria de alterações na tabela locacao. */
public record RegistroAuditoria(
        int idAuditoria,
        int idLocacao,
        String operacao,
        String usuarioBanco,
        LocalDateTime dataHora,
        Integer idFuncionarioAcao,
        String nomeFuncionario,
        String dadosAntigosJson,
        String dadosNovosJson) {
}
