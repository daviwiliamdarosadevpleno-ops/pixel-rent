package br.unoesc.pixelrent.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ItemHistoricoLocacao(
        int idCliente,
        String cliente,
        int idLocacao,
        String tituloJogo,
        String plataforma,
        LocalDate dataLocacao,
        LocalDate dataDevolucao,
        BigDecimal valorDiaria,
        BigDecimal multa,
        String status) { }
