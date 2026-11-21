package br.unoesc.pixelrent.exception;

/**
 * Indica que a configuração da aplicação está ausente ou inválida
 * (por exemplo, nome do banco não informado ou porta fora do intervalo).
 *
 * <p>As mensagens desta exceção nunca devem conter senhas ou outros segredos.</p>
 */
public class ConfiguracaoException extends PixelRentException {

    private static final long serialVersionUID = 1L;

    /**
     * Cria a exceção com uma mensagem.
     *
     * @param mensagem descrição do problema de configuração (sem segredos)
     */
    public ConfiguracaoException(String mensagem) {
        super(mensagem);
    }

    /**
     * Cria a exceção com uma mensagem e a causa original.
     *
     * @param mensagem descrição do problema de configuração (sem segredos)
     * @param causa    exceção que originou o problema
     */
    public ConfiguracaoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
