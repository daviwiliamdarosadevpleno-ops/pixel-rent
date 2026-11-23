package br.unoesc.pixelrent.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Representa um cliente cadastrado no Pixel Rent.
 */
public record Cliente(
        int idCliente,
        String nome,
        String cpf,
        String telefone,
        String email,
        boolean ativo,
        LocalDate dataCadastro) {

    public Cliente {
        if (idCliente <= 0) {
            throw new IllegalArgumentException(
                    "O identificador do cliente deve ser positivo.");
        }

        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException(
                    "O nome do cliente é obrigatório.");
        }

        if (cpf == null || cpf.isBlank()) {
            throw new IllegalArgumentException(
                    "O CPF do cliente é obrigatório.");
        }

        nome = nome.trim();
        cpf = cpf.trim();
        telefone = normalizarOpcional(telefone);
        email = normalizarOpcional(email);

        Objects.requireNonNull(dataCadastro, "dataCadastro");
    }

    private static String normalizarOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }

        return valor.trim();
    }
}