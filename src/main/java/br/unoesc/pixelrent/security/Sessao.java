package br.unoesc.pixelrent.security;

import br.unoesc.pixelrent.config.PerfilConexao;

import java.util.Objects;

/** Identidade autenticada do funcionário atual; não contém senha nem hash. */
public record Sessao(int idFuncionario, String nome, PerfilFuncionario perfil) {

    public Sessao {
        if (idFuncionario <= 0) {
            throw new IllegalArgumentException("O identificador do funcionário deve ser positivo.");
        }
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome do funcionário não pode ser vazio.");
        }
        nome = nome.trim();
        Objects.requireNonNull(perfil, "perfil");
    }

    /**
     * Perfil de conexão a utilizar nas próximas operações do sistema.
     * A interface não deve escolher esse perfil por conta própria.
     *
     * @return perfil PostgreSQL correspondente ao perfil autenticado
     */
    public PerfilConexao perfilConexao() {
        return perfil.perfilConexao();
    }
}
