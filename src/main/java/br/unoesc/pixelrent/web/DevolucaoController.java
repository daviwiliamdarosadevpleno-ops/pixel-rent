package br.unoesc.pixelrent.web;

import br.unoesc.pixelrent.exception.DadosInvalidosException;
import br.unoesc.pixelrent.security.Sessao;
import br.unoesc.pixelrent.service.OperacaoService;

import java.math.BigDecimal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/devolucoes")
public class DevolucaoController {
    private final OperacaoService service;

    public DevolucaoController(OperacaoService service) { this.service = service; }

    @GetMapping
    public String listar(@AuthenticationPrincipal Sessao sessao, Model model) {
        model.addAttribute("itens", service.listarItensPendentes(sessao));
        return "devolucoes/lista";
    }

    @PostMapping("/{idLocacao}/{idMidia}")
    public String devolver(@PathVariable int idLocacao,
                           @PathVariable int idMidia,
                           @RequestParam(defaultValue = "false") boolean comDefeito,
                           @AuthenticationPrincipal Sessao sessao,
                           RedirectAttributes redirect) {
        try {
            BigDecimal multa = service.registrarDevolucao(sessao, idLocacao, idMidia, comDefeito);
            String mensagem = "Devolução registrada. Multa calculada: R$ " + multa.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + ".";
            if (comDefeito) mensagem += " A mídia foi encaminhada para manutenção.";
            redirect.addFlashAttribute("sucesso", mensagem);
        } catch (DadosInvalidosException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/devolucoes";
    }
}
