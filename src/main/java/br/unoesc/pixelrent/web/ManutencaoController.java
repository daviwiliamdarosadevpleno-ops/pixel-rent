package br.unoesc.pixelrent.web;

import br.unoesc.pixelrent.exception.DadosInvalidosException;
import br.unoesc.pixelrent.security.Sessao;
import br.unoesc.pixelrent.service.OperacaoService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/manutencao")
public class ManutencaoController {
    private final OperacaoService service;

    public ManutencaoController(OperacaoService service) { this.service = service; }

    @GetMapping
    public String listar(@AuthenticationPrincipal Sessao sessao, Model model) {
        model.addAttribute("midias", service.listarManutencao(sessao));
        return "manutencao/lista";
    }

    @PostMapping("/{idMidia}/liberar")
    public String liberar(@PathVariable int idMidia,
                          @AuthenticationPrincipal Sessao sessao,
                          RedirectAttributes redirect) {
        try {
            if (service.liberarManutencao(sessao, idMidia)) {
                redirect.addFlashAttribute("sucesso", "Mídia liberada e retornada à disponibilidade.");
            } else {
                redirect.addFlashAttribute("erro", "A mídia não está em manutenção ou não foi encontrada.");
            }
        } catch (DadosInvalidosException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/manutencao";
    }
}
