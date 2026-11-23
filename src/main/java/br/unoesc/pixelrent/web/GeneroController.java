package br.unoesc.pixelrent.web;

import br.unoesc.pixelrent.exception.DadosInvalidosException;
import br.unoesc.pixelrent.model.RegistroCatalogo;
import br.unoesc.pixelrent.security.Sessao;
import br.unoesc.pixelrent.service.GeneroService;

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
@RequestMapping("/generos")
public class GeneroController {

    private static final String TITULO = "Gêneros";
    private static final String DESCRICAO = "Gerencie as categorias utilizadas para classificar os jogos.";
    private final GeneroService service;

    public GeneroController(GeneroService service) {
        this.service = service;
    }

    @GetMapping
    public String listar(@AuthenticationPrincipal Sessao sessao,
                         @RequestParam(required = false) String pesquisa,
                         Model model) {
        List<RegistroCatalogo> itens = service.listar(sessao, pesquisa);
        prepararLista(model, itens, pesquisa);
        return "catalogos/lista";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        prepararFormulario(model, new NomeCadastroForm(), false, "/generos", null);
        return "catalogos/formulario";
    }

    @PostMapping
    public String cadastrar(@AuthenticationPrincipal Sessao sessao,
                            @ModelAttribute NomeCadastroForm nomeForm,
                            Model model,
                            RedirectAttributes redirect) {
        try {
            service.cadastrar(sessao, nomeForm.getNome());
            redirect.addFlashAttribute("sucesso", "Gênero cadastrado com sucesso.");
            return "redirect:/generos";
        } catch (DadosInvalidosException e) {
            prepararFormulario(model, nomeForm, false, "/generos", e.getMessage());
            return "catalogos/formulario";
        }
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable int id,
                         @AuthenticationPrincipal Sessao sessao,
                         Model model,
                         RedirectAttributes redirect) {
        Optional<RegistroCatalogo> encontrado = service.buscarPorId(sessao, id);
        if (encontrado.isEmpty()) {
            redirect.addFlashAttribute("erro", "Gênero não encontrado.");
            return "redirect:/generos";
        }
        prepararFormulario(model, new NomeCadastroForm(encontrado.get().nome()), true,
                "/generos/" + id, null);
        model.addAttribute("registroId", id);
        return "catalogos/formulario";
    }

    @PostMapping("/{id}")
    public String atualizar(@PathVariable int id,
                            @AuthenticationPrincipal Sessao sessao,
                            @ModelAttribute NomeCadastroForm nomeForm,
                            Model model,
                            RedirectAttributes redirect) {
        try {
            Optional<RegistroCatalogo> atualizado = service.atualizar(sessao, id, nomeForm.getNome());
            if (atualizado.isEmpty()) {
                redirect.addFlashAttribute("erro", "Gênero não encontrado.");
                return "redirect:/generos";
            }
            redirect.addFlashAttribute("sucesso", "Gênero atualizado com sucesso.");
            return "redirect:/generos";
        } catch (DadosInvalidosException e) {
            prepararFormulario(model, nomeForm, true, "/generos/" + id, e.getMessage());
            model.addAttribute("registroId", id);
            return "catalogos/formulario";
        }
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable int id,
                          @AuthenticationPrincipal Sessao sessao,
                          RedirectAttributes redirect) {
        try {
            if (service.excluir(sessao, id)) {
                redirect.addFlashAttribute("sucesso", "Gênero excluído com sucesso.");
            } else {
                redirect.addFlashAttribute("erro", "Gênero não encontrado.");
            }
        } catch (DadosInvalidosException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/generos";
    }

    private void prepararLista(Model model, List<RegistroCatalogo> itens, String pesquisa) {
        model.addAttribute("itens", itens);
        model.addAttribute("pesquisa", pesquisa == null ? "" : pesquisa);
        model.addAttribute("tituloModulo", TITULO);
        model.addAttribute("descricaoModulo", DESCRICAO);
        model.addAttribute("nomeSingular", "gênero");
        model.addAttribute("urlBase", "/generos");
    }

    private void prepararFormulario(Model model, NomeCadastroForm form,
                                    boolean modoEdicao, String urlAcao, String erro) {
        model.addAttribute("nomeForm", form);
        model.addAttribute("modoEdicao", modoEdicao);
        model.addAttribute("urlAcao", urlAcao);
        model.addAttribute("urlLista", "/generos");
        model.addAttribute("tituloModulo", TITULO);
        model.addAttribute("nomeSingular", "gênero");
        model.addAttribute("erro", erro);
    }
}
