package br.unoesc.pixelrent.exception;

/** Indica uma falha técnica; a causa fica disponível para registro interno, não para a tela. */
public class FalhaTecnicaException extends PixelRentException {

    private static final long serialVersionUID = 1L;

    public FalhaTecnicaException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
