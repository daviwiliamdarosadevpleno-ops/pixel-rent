
package br.unoesc.pixelrent.web;

import br.unoesc.pixelrent.exception.AcessoNegadoException;
import br.unoesc.pixelrent.exception.ConfiguracaoException;
import br.unoesc.pixelrent.exception.DadosInvalidosException;
import br.unoesc.pixelrent.exception.FalhaTecnicaException;
import br.unoesc.pixelrent.exception.PixelRentException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
/**
 * Centraliza o tratamento das exceções da aplicação web.
 */
@ControllerAdvice(annotations = Controller.class)
public class TratamentoErrosWeb {
	 private static final Logger LOGGER =
	            LoggerFactory.getLogger(TratamentoErrosWeb.class);

    @ExceptionHandler(AcessoNegadoException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String tratarAcessoNegado() {
        return "error/403";
    }

    @ExceptionHandler(DadosInvalidosException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String tratarDadosInvalidos(
            DadosInvalidosException erro,
            Model model) {

        model.addAttribute("mensagem", erro.getMessage());

        return "error/400";
    }

    @ExceptionHandler({
        ConfiguracaoException.class,
        FalhaTecnicaException.class
})
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public String tratarFalhaTecnica(Exception erro) {
    LOGGER.error("Falha técnica ao processar requisição web.", erro);
    return "error/500";
}
    @ExceptionHandler(PixelRentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String tratarErroAplicacao(
            PixelRentException erro,
            Model model) {

        model.addAttribute("mensagem", erro.getMessage());

        return "error/400";
    }
}
