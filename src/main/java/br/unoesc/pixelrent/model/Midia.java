package br.unoesc.pixelrent.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Representa uma unidade física ou licença digital do catálogo. */
public record Midia(
        int idMidia,
        int idJogo,
        String tituloJogo,
        int idPlataforma,
        String nomePlataforma,
        String tipo,
        String status,
        BigDecimal valorDiaria,
        String codigoBarras,
        String estadoConservacao,
        String codigoLicenca,
        LocalDate validade) {
}
