package br.unoesc.pixelrent.model;

import java.math.BigDecimal;

public record MidiaOpcaoLocacao(
        int idMidia,
        String tituloJogo,
        String genero,
        String plataforma,
        String tipo,
        String identificador,
        BigDecimal valorDiaria) { }
