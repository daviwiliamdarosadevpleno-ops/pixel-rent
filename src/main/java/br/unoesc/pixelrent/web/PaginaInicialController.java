package br.unoesc.pixelrent.web;

import br.unoesc.pixelrent.security.Sessao;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PaginaInicialController {

    @GetMapping("/")
    public String paginaInicial(
            @AuthenticationPrincipal Sessao sessao,
            Model model) {

        model.addAttribute("sessao", sessao);

        return "index";
    }
}