package br.unoesc.pixelrent.model;

import java.util.List;

/** Representa um jogo e as plataformas associadas no catálogo. */
public record Jogo(
        int idJogo,
        String titulo,
        int anoLancamento,
        String classificacaoIndicativa,
        int idGenero,
        String nomeGenero,
        List<Integer> idsPlataformas,
        String nomesPlataformas) {

    public Jogo {
        idsPlataformas = idsPlataformas == null ? List.of() : List.copyOf(idsPlataformas);
        nomesPlataformas = nomesPlataformas == null ? "" : nomesPlataformas;
    }
}
