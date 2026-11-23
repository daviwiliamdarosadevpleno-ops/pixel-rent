package br.unoesc.pixelrent.model;

import br.unoesc.pixelrent.security.PerfilFuncionario;

/** Dados administrativos de funcionário; nunca inclui a senha ou seu hash. */
public record Funcionario(
        int idFuncionario,
        String nome,
        String cpf,
        String telefone,
        String email,
        String login,
        PerfilFuncionario perfil,
        boolean ativo) {
}
