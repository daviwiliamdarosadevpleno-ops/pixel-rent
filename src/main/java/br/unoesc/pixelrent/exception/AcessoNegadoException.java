package br.unoesc.pixelrent.exception;

/** Indica credenciais recusadas ou tentativa de acessar uma funcionalidade não autorizada. */
public class AcessoNegadoException extends PixelRentException {

    private static final long serialVersionUID = 1L;

    public AcessoNegadoException(String mensagem) {
        super(mensagem);
    }
}
