package br.unoesc.pixelrent.exception;

/**
 * Exceção base (não verificada) das falhas próprias da aplicação Pixel Rent.
 *
 * <p>Todas as exceções específicas da aplicação herdam desta classe, o que permite
 * capturá-las de forma uniforme na camada de interface.</p>
 */
public class PixelRentException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Cria a exceção com uma mensagem.
     *
     * @param mensagem descrição do problema, adequada para ser exibida ao usuário
     */
    public PixelRentException(String mensagem) {
        super(mensagem);
    }

    /**
     * Cria a exceção com uma mensagem e a causa original.
     *
     * @param mensagem descrição do problema, adequada para ser exibida ao usuário
     * @param causa    exceção que originou o problema
     */
    public PixelRentException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
