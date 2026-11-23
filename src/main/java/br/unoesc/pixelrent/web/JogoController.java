package br.unoesc.pixelrent.web;

import br.unoesc.pixelrent.exception.DadosInvalidosException;
import br.unoesc.pixelrent.model.Jogo;
import br.unoesc.pixelrent.model.RegistroCatalogo;
import br.unoesc.pixelrent.security.PerfilFuncionario;
import br.unoesc.pixelrent.security.Sessao;
import br.unoesc.pixelrent.service.JogoService;

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
@RequestMapping("/jogos")
public class JogoController {
    private final JogoService service;

    public JogoController(JogoService service) { this.service = service; }

    @GetMapping
    public String listar(@AuthenticationPrincipal Sessao sessao,
                         @RequestParam(required = false) String pesquisa,
                         Model model) {
        List<Jogo> jogos = service.listar(sessao, pesquisa);
        model.addAttribute("jogos", jogos);
        model.addAttribute("pesquisa", pesquisa == null ? "" : pesquisa);
        model.addAttribute("podeExcluir", sessao.perfil() == PerfilFuncionario.ADMINISTRADOR);
        return "jogos/lista";
    }

    @GetMapping("/novo")
    public String novo(@AuthenticationPrincipal Sessao sessao, Model model) {
        prepararFormulario(sessao, model, new JogoForm(), false, "/jogos", null);
        return "jogos/formulario";
    }

    @PostMapping
    public String cadastrar(@AuthenticationPrincipal Sessao sessao,
                            @ModelAttribute JogoForm form,
                            Model model, RedirectAttributes redirect) {
        try {
            int id = service.cadastrar(sessao, form.getTitulo(), form.getAnoLancamento(),
                    form.getClassificacaoIndicativa(), form.getIdGenero(), form.getPlataformas());
            redirect.addFlashAttribute("sucesso", "Jogo cadastrado com sucesso (ID " + id + ").");
            return "redirect:/jogos";
        } catch (DadosInvalidosException e) {
            prepararFormulario(sessao, model, form, false, "/jogos", e.getMessage());
            return "jogos/formulario";
        }
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable int id, @AuthenticationPrincipal Sessao sessao,
                         Model model, RedirectAttributes redirect) {
        Optional<Jogo> encontrado = service.buscarPorId(sessao, id);
        if (encontrado.isEmpty()) {
            redirect.addFlashAttribute("erro", "Jogo não encontrado.");
            return "redirect:/jogos";
        }
        Jogo jogo = encontrado.get();
        JogoForm form = new JogoForm();
        form.setTitulo(jogo.titulo());
        form.setAnoLancamento(jogo.anoLancamento());
        form.setClassificacaoIndicativa(jogo.classificacaoIndicativa());
        form.setIdGenero(jogo.idGenero());
        form.setPlataformas(jogo.idsPlataformas());
        prepararFormulario(sessao, model, form, true, "/jogos/" + id, null);
        model.addAttribute("jogoId", id);
        return "jogos/formulario";
    }

    @PostMapping("/{id}")
    public String atualizar(@PathVariable int id, @AuthenticationPrincipal Sessao sessao,
                            @ModelAttribute JogoForm form, Model model,
                            RedirectAttributes redirect) {
        try {
            if (!service.atualizar(sessao, id, form.getTitulo(), form.getAnoLancamento(),
                    form.getClassificacaoIndicativa(), form.getIdGenero(), form.getPlataformas())) {
                redirect.addFlashAttribute("erro", "Jogo não encontrado.");
            } else {
                redirect.addFlashAttribute("sucesso", "Jogo atualizado com sucesso.");
            }
            return "redirect:/jogos";
        } catch (DadosInvalidosException e) {
            prepararFormulario(sessao, model, form, true, "/jogos/" + id, e.getMessage());
            model.addAttribute("jogoId", id);
            return "jogos/formulario";
        }
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable int id, @AuthenticationPrincipal Sessao sessao,
                          RedirectAttributes redirect) {
        try {
            if (service.excluir(sessao, id)) redirect.addFlashAttribute("sucesso", "Jogo excluído com sucesso.");
            else redirect.addFlashAttribute("erro", "Jogo não encontrado.");
        } catch (DadosInvalidosException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/jogos";
    }

    private void prepararFormulario(Sessao sessao, Model model, JogoForm form,
                                    boolean edicao, String acao, String erro) {
        List<RegistroCatalogo> generos = service.listarGeneros(sessao);
        List<RegistroCatalogo> plataformas = service.listarPlataformas(sessao);
        model.addAttribute("jogoForm", form);
        model.addAttribute("generos", generos);
        model.addAttribute("plataformas", plataformas);
        model.addAttribute("podeAlterarPlataformas",
                sessao.perfil() == PerfilFuncionario.ADMINISTRADOR);
        model.addAttribute("modoEdicao", edicao);
        model.addAttribute("urlAcao", acao);
        model.addAttribute("erro", erro);
    }
}
