package br.unoesc.pixelrent.model;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/** Resultado agregado de um mês no relatório financeiro. */
public record FaturamentoMensal(YearMonth mes, long quantidadeLocacoes, BigDecimal faturamento) {
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("MM/yyyy");

    public String mesFormatado() {
        return mes.format(FORMATO);
    }
}
