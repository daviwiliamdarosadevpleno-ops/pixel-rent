package br.unoesc.pixelrent.web;

import br.unoesc.pixelrent.exception.DadosInvalidosException;
import br.unoesc.pixelrent.security.Sessao;
import br.unoesc.pixelrent.service.OperacaoService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/locacoes")
public class LocacaoController {
    private final OperacaoService service;

    public LocacaoController(OperacaoService service) { this.service = service; }

    @GetMapping
    public String listar(@AuthenticationPrincipal Sessao sessao, Model model) {
        model.addAttribute("itens", service.listarLocacoesAtivas(sessao));
        return "locacoes/lista";
    }

    @GetMapping("/nova")
    public String nova(@AuthenticationPrincipal Sessao sessao, Model model) {
        prepararFormulario(sessao, model, new LocacaoForm(), null);
        return "locacoes/formulario";
    }

    @PostMapping
    public String criar(@AuthenticationPrincipal Sessao sessao,
                        @ModelAttribute LocacaoForm form,
                        Model model, RedirectAttributes redirect) {
        try {
            int id = service.criarLocacao(sessao, form.getIdCliente(), form.getIdsMidias(), form.getDias());
            redirect.addFlashAttribute("sucesso", "Locação #" + id + " registrada com sucesso.");
            return "redirect:/locacoes";
        } catch (DadosInvalidosException e) {
            prepararFormulario(sessao, model, form, e.getMessage());
            return "locacoes/formulario";
        }
    }

    private void prepararFormulario(Sessao sessao, Model model, LocacaoForm form, String erro) {
        model.addAttribute("form", form);
        model.addAttribute("clientes", service.listarClientesAtivos(sessao));
        model.addAttribute("midiasDisponiveis", service.listarMidiasDisponiveis(sessao));
        model.addAttribute("erro", erro);
    }
}
