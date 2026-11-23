package br.unoesc.pixelrent.web;

import br.unoesc.pixelrent.model.PainelConsultas;
import br.unoesc.pixelrent.security.Sessao;
import br.unoesc.pixelrent.service.OperacaoService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/consultas")
public class ConsultaController {
    private final OperacaoService service;

    public ConsultaController(OperacaoService service) { this.service = service; }

    @GetMapping
    public String consultar(@AuthenticationPrincipal Sessao sessao, Model model) {
        PainelConsultas painel = service.consultarPainel(sessao);
        model.addAttribute("painel", painel);
        return "consultas/index";
    }
}
