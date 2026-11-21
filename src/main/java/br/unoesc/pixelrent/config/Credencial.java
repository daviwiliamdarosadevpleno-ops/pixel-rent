package br.unoesc.pixelrent.config;

/**
 * Par usuário/senha de um role do banco de dados.
 *
 * <p>A senha nunca aparece em {@link #toString()}, para evitar vazamento acidental
 * em logs e mensagens de erro.</p>
 *
 * @param usuario nome do role no PostgreSQL (não vazio)
 * @param senha   senha do role (não vazia)
 */
public record Credencial(String usuario, String senha) {

    /**
     * Valida os componentes.
     *
     * @throws IllegalArgumentException se o usuário ou a senha estiverem vazios
     */
    public Credencial {
        if (usuario == null || usuario.isBlank()) {
            throw new IllegalArgumentException("O usuário do banco não pode ser vazio.");
        }
        if (senha == null || senha.isEmpty()) {
            throw new IllegalArgumentException("A senha do banco não pode ser vazia.");
        }
    }

    @Override
    public String toString() {
        return "Credencial[usuario=" + usuario + ", senha=****]";
    }
}
