package br.unoesc.pixelrent.model;

/** Representa um registro simples do catálogo (gênero ou plataforma). */
public record RegistroCatalogo(int id, String nome) {

    public RegistroCatalogo {
        if (id <= 0) {
            throw new IllegalArgumentException("O identificador deve ser positivo.");
        }
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome é obrigatório.");
        }
        nome = nome.trim();
        if (nome.length() > 50) {
            throw new IllegalArgumentException("O nome deve possuir no máximo 50 caracteres.");
        }
    }
}
