package br.unoesc.pixelrent.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ItemLocacaoAtiva(
        int idLocacao,
        int idItemLocacao,
        int idMidia,
        String cliente,
        String funcionario,
        String tituloJogo,
        String plataforma,
        LocalDate dataLocacao,
        LocalDate prazoDevolucao,
        String status,
        BigDecimal valorDiaria) { }
