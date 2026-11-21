package br.unoesc.pixelrent.exception;

/** Indica que um dado obrigatório ou seu formato não passou pela validação da aplicação. */
public class DadosInvalidosException extends PixelRentException {

    private static final long serialVersionUID = 1L;

    public DadosInvalidosException(String mensagem) {
        super(mensagem);
    }
}
