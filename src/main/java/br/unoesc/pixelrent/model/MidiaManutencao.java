package br.unoesc.pixelrent.model;

import java.time.LocalDate;

public record MidiaManutencao(
        int idMidia,
        String tituloJogo,
        String plataforma,
        String tipo,
        String identificador,
        LocalDate validadeLicenca) { }
