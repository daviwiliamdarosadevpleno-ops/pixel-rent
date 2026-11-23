package br.unoesc.pixelrent.web;

import br.unoesc.pixelrent.exception.DadosInvalidosException;
import br.unoesc.pixelrent.model.Funcionario;
import br.unoesc.pixelrent.security.PerfilFuncionario;
import br.unoesc.pixelrent.security.Sessao;
import br.unoesc.pixelrent.service.FuncionarioService;

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
@RequestMapping("/funcionarios")
public class FuncionarioController {
    private final FuncionarioService service;

    public FuncionarioController(FuncionarioService service) {
        this.service = service;
    }

    @GetMapping
    public String listar(@AuthenticationPrincipal Sessao sessao,
            @RequestParam(defaultValue = "false") boolean incluirInativos,
            @RequestParam(required = false) String pesquisa, Model model) {
        List<Funcionario> funcionarios = service.listar(sessao, incluirInativos, pesquisa);
        model.addAttribute("funcionarios", funcionarios);
        model.addAttribute("sessao", sessao);
        model.addAttribute("incluirInativos", incluirInativos);
        model.addAttribute("pesquisa", pesquisa == null ? "" : pesquisa);
        return "funcionarios/lista";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        prepararFormulario(model, new FuncionarioForm(), false, "/funcionarios", null);
        return "funcionarios/formulario";
    }

    @PostMapping
    public String cadastrar(@AuthenticationPrincipal Sessao sessao,
            @ModelAttribute FuncionarioForm form, Model model, RedirectAttributes redirect) {
        try {
            service.cadastrar(sessao, form.getNome(), form.getCpf(), form.getTelefone(),
                    form.getEmail(), form.getLogin(), form.getSenha(), form.getConfirmarSenha(), form.getPerfil());
            redirect.addFlashAttribute("sucesso", "Funcionário cadastrado com sucesso.");
            return "redirect:/funcionarios";
        } catch (DadosInvalidosException e) {
            form.setSenha(null);
            form.setConfirmarSenha(null);
            prepararFormulario(model, form, false, "/funcionarios", e.getMessage());
            return "funcionarios/formulario";
        }
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable int id, @AuthenticationPrincipal Sessao sessao,
            Model model, RedirectAttributes redirect) {
        Optional<Funcionario> encontrado = service.buscarPorId(sessao, id);
        if (encontrado.isEmpty()) {
            redirect.addFlashAttribute("erro", "Funcionário não encontrado.");
            return "redirect:/funcionarios";
        }
        Funcionario funcionario = encontrado.get();
        FuncionarioForm form = new FuncionarioForm();
        form.setNome(funcionario.nome());
        form.setCpf(funcionario.cpf());
        form.setTelefone(funcionario.telefone());
        form.setEmail(funcionario.email());
        form.setLogin(funcionario.login());
        form.setPerfil(funcionario.perfil());
        prepararFormulario(model, form, true, "/funcionarios/" + id, null);
        model.addAttribute("funcionarioId", id);
        return "funcionarios/formulario";
    }

    @PostMapping("/{id}")
    public String atualizar(@PathVariable int id, @AuthenticationPrincipal Sessao sessao,
            @ModelAttribute FuncionarioForm form, Model model, RedirectAttributes redirect) {
        try {
            Optional<Funcionario> resultado = service.atualizar(sessao, id, form.getNome(), form.getCpf(),
                    form.getTelefone(), form.getEmail(), form.getLogin(), form.getSenha(),
                    form.getConfirmarSenha(), form.getPerfil());
            if (resultado.isEmpty()) {
                redirect.addFlashAttribute("erro", "Funcionário não encontrado.");
                return "redirect:/funcionarios";
            }
            redirect.addFlashAttribute("sucesso", "Funcionário atualizado com sucesso.");
            return "redirect:/funcionarios";
        } catch (DadosInvalidosException e) {
            form.setSenha(null);
            form.setConfirmarSenha(null);
            prepararFormulario(model, form, true, "/funcionarios/" + id, e.getMessage());
            model.addAttribute("funcionarioId", id);
            return "funcionarios/formulario";
        }
    }

    @PostMapping("/{id}/situacao")
    public String alterarSituacao(@PathVariable int id, @AuthenticationPrincipal Sessao sessao,
            @RequestParam boolean ativo, RedirectAttributes redirect) {
        try {
            boolean alterado = service.alterarAtivo(sessao, id, ativo);
            if (alterado) {
                redirect.addFlashAttribute("sucesso", ativo
                        ? "Funcionário ativado com sucesso." : "Funcionário desativado com sucesso.");
            } else {
                redirect.addFlashAttribute("erro", "Funcionário não encontrado.");
            }
        } catch (DadosInvalidosException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/funcionarios";
    }

    private void prepararFormulario(Model model, FuncionarioForm form,
            boolean modoEdicao, String urlAcao, String erro) {
        model.addAttribute("funcionarioForm", form);
        model.addAttribute("modoEdicao", modoEdicao);
        model.addAttribute("urlAcao", urlAcao);
        model.addAttribute("erro", erro);
        model.addAttribute("perfis", List.of(PerfilFuncionario.values()));
    }
}
