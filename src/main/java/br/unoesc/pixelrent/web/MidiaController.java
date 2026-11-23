package br.unoesc.pixelrent.web;

import br.unoesc.pixelrent.exception.DadosInvalidosException;
import br.unoesc.pixelrent.model.Midia;
import br.unoesc.pixelrent.model.OpcaoJogoPlataforma;
import br.unoesc.pixelrent.security.Sessao;
import br.unoesc.pixelrent.service.MidiaService;

import java.util.List;
import java.util.Optional;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/midias")
public class MidiaController {
    private final MidiaService service;

    public MidiaController(MidiaService service) { this.service = service; }

    @GetMapping
    public String listar(@AuthenticationPrincipal Sessao sessao,
                         @RequestParam(required = false) String pesquisa,
                         Model model) {
        List<Midia> midias = service.listar(sessao, pesquisa);
        model.addAttribute("midias", midias);
        model.addAttribute("pesquisa", pesquisa == null ? "" : pesquisa);
        return "midias/lista";
    }

    @GetMapping("/novo")
    public String novo(@AuthenticationPrincipal Sessao sessao, Model model) {
        prepararFormulario(sessao, model, new MidiaForm(), false, "/midias", null);
        return "midias/formulario";
    }

    @PostMapping
    public String cadastrar(@AuthenticationPrincipal Sessao sessao,
                            @ModelAttribute MidiaForm form,
                            Model model, RedirectAttributes redirect) {
        try {
            int id = service.cadastrar(sessao, form.getIdJogoPlataforma(), form.getTipo(),
                    form.getValorDiaria(), form.getCodigoBarras(), form.getEstadoConservacao(),
                    form.getCodigoLicenca(), form.getValidade());
            redirect.addFlashAttribute("sucesso", "Mídia cadastrada com sucesso (ID " + id + ").");
            return "redirect:/midias";
        } catch (DadosInvalidosException e) {
            prepararFormulario(sessao, model, form, false, "/midias", e.getMessage());
            return "midias/formulario";
        }
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable int id, @AuthenticationPrincipal Sessao sessao,
                         Model model, RedirectAttributes redirect) {
        Optional<Midia> encontrado = service.buscarPorId(sessao, id);
        if (encontrado.isEmpty()) {
            redirect.addFlashAttribute("erro", "Mídia não encontrada.");
            return "redirect:/midias";
        }
        Midia midia = encontrado.get();
        MidiaForm form = new MidiaForm();
        form.setIdJogoPlataforma(midia.idJogo() + ":" + midia.idPlataforma());
        form.setTipo(midia.tipo());
        form.setValorDiaria(midia.valorDiaria());
        form.setCodigoBarras(midia.codigoBarras());
        form.setEstadoConservacao(midia.estadoConservacao());
        form.setCodigoLicenca(midia.codigoLicenca());
        form.setValidade(midia.validade());
        prepararFormulario(sessao, model, form, true, "/midias/" + id, null);
        model.addAttribute("midia", midia);
        model.addAttribute("midiaId", id);
        return "midias/formulario";
    }

    @PostMapping("/{id}")
    public String atualizar(@PathVariable int id, @AuthenticationPrincipal Sessao sessao,
                            @ModelAttribute MidiaForm form, Model model,
                            RedirectAttributes redirect) {
        try {
            if (service.atualizar(sessao, id, form.getTipo(), form.getValorDiaria(),
                    form.getCodigoBarras(), form.getEstadoConservacao(),
                    form.getCodigoLicenca(), form.getValidade())) {
                redirect.addFlashAttribute("sucesso", "Mídia atualizada com sucesso.");
            } else {
                redirect.addFlashAttribute("erro", "Mídia não encontrada.");
            }
            return "redirect:/midias";
        } catch (DadosInvalidosException e) {
            prepararFormulario(sessao, model, form, true, "/midias/" + id, e.getMessage());
            model.addAttribute("midiaId", id);
            service.buscarPorId(sessao, id).ifPresent(m -> model.addAttribute("midia", m));
            return "midias/formulario";
        }
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable int id, @AuthenticationPrincipal Sessao sessao,
                          RedirectAttributes redirect) {
        try {
            if (service.excluir(sessao, id)) redirect.addFlashAttribute("sucesso", "Mídia excluída com sucesso.");
            else redirect.addFlashAttribute("erro", "A mídia não foi encontrada ou não está disponível.");
        } catch (DadosInvalidosException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/midias";
    }

    private void prepararFormulario(Sessao sessao, Model model, MidiaForm form,
                                    boolean edicao, String acao, String erro) {
        List<OpcaoJogoPlataforma> opcoes = service.listarOpcoes(sessao);
        model.addAttribute("midiaForm", form);
        model.addAttribute("opcoesJogoPlataforma", opcoes);
        model.addAttribute("modoEdicao", edicao);
        model.addAttribute("urlAcao", acao);
        model.addAttribute("erro", erro);
    }
}
