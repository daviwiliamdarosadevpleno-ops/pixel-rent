package br.unoesc.pixelrent.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LocacaoAtrasada(
        int idLocacao,
        String cliente,
        String telefone,
        String tituloJogo,
        LocalDate prazoDevolucao,
        int diasAtraso,
        BigDecimal multaEstimada) { }
