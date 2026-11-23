package br.unoesc.pixelrent.web;

import br.unoesc.pixelrent.security.Sessao;
import br.unoesc.pixelrent.service.AuditoriaService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/auditoria")
public class AuditoriaController {
    private final AuditoriaService service;

    public AuditoriaController(AuditoriaService service) { this.service = service; }

    @GetMapping
    public String listar(@AuthenticationPrincipal Sessao sessao,
            @RequestParam(required = false) Integer idLocacao,
            @RequestParam(required = false) String operacao, Model model) {
        var registros = service.listar(sessao, idLocacao, operacao);
        model.addAttribute("registros", registros);
        model.addAttribute("idLocacao", idLocacao);
        model.addAttribute("operacao", operacao == null ? "" : operacao);
        return "auditoria/index";
    }
}
