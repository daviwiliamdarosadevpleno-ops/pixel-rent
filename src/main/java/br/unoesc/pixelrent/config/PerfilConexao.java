package br.unoesc.pixelrent.config;

/**
 * Perfis de conexão com o banco de dados.
 *
 * <p>Cada perfil corresponde a um conjunto de credenciais e, portanto, a um role do
 * PostgreSQL com privilégios distintos:</p>
 * <ul>
 *   <li>{@link #ATENDENTE}: role membro do grupo {@code grp_atendente}; usado pelo
 *       funcionário de perfil {@code FUNCIONARIO}.</li>
 *   <li>{@link #ADMINISTRADOR}: role membro do grupo {@code grp_admin}; usado pelo
 *       funcionário de perfil {@code ADMINISTRADOR}.</li>
 * </ul>
 *
 * <p>Não confundir com o perfil de negócio do funcionário (coluna
 * {@code funcionario.perfil}), que será tratado na etapa de autenticação.</p>
 */
public enum PerfilConexao {

    /** Conexão com os privilégios do grupo {@code grp_atendente}. */
    ATENDENTE("atendente"),

    /** Conexão com os privilégios do grupo {@code grp_admin}. */
    ADMINISTRADOR("admin");

    private final String chave;

    PerfilConexao(String chave) {
        this.chave = chave;
    }

    /**
     * Chave de configuração do usuário do banco deste perfil.
     *
     * @return por exemplo {@code db.atendente.user}
     */
    public String chaveUsuario() {
        return "db." + chave + ".user";
    }

    /**
     * Chave de configuração da senha do banco deste perfil.
     *
     * @return por exemplo {@code db.atendente.password}
     */
    public String chaveSenha() {
        return "db." + chave + ".password";
    }
}
