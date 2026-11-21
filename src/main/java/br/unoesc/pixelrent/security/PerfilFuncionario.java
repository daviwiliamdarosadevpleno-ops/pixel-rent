package br.unoesc.pixelrent.security;

import br.unoesc.pixelrent.config.PerfilConexao;

import java.util.Locale;

/** Perfil de negócio armazenado em {@code funcionario.perfil}. */
public enum PerfilFuncionario {

    /** Funcionário que atende e opera locações. */
    FUNCIONARIO("Funcionário"),

    /** Funcionário com atribuições administrativas. */
    ADMINISTRADOR("Administrador");

    private final String descricao;

    PerfilFuncionario(String descricao) {
        this.descricao = descricao;
    }

    /**
     * Converte o valor retornado pelo banco para um perfil conhecido.
     *
     * @param valor valor de {@code funcionario.perfil}
     * @return perfil correspondente
     * @throws IllegalArgumentException se o banco retornar um perfil desconhecido
     */
    public static PerfilFuncionario deBanco(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O banco retornou um perfil vazio.");
        }
        return switch (valor.trim().toUpperCase(Locale.ROOT)) {
            case "FUNCIONARIO" -> FUNCIONARIO;
            case "ADMINISTRADOR" -> ADMINISTRADOR;
            default -> throw new IllegalArgumentException("Perfil de funcionário não reconhecido.");
        };
    }

    /**
     * Retorna o perfil de conexão PostgreSQL apropriado para operações posteriores ao login.
     * A conexão de autenticação inicial continua sendo sempre a de menor privilégio.
     *
     * @return credenciais do atendente ou do administrador
     */
    public PerfilConexao perfilConexao() {
        return this == ADMINISTRADOR ? PerfilConexao.ADMINISTRADOR : PerfilConexao.ATENDENTE;
    }

    /** @return descrição amigável para a interface */
    public String descricao() {
        return descricao;
    }
}
