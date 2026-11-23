
package br.unoesc.pixelrent.web;

import br.unoesc.pixelrent.exception.DadosInvalidosException;
import br.unoesc.pixelrent.model.Cliente;
import br.unoesc.pixelrent.security.Sessao;
import br.unoesc.pixelrent.service.ClienteService;

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
@RequestMapping("/clientes")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    public String listar(
            @AuthenticationPrincipal Sessao sessao,
            @RequestParam(defaultValue = "false") boolean incluirInativos,
            @RequestParam(required = false) String pesquisa,
            Model model) {

        List<Cliente> clientes = clienteService.listar(
                sessao, incluirInativos, pesquisa);

        model.addAttribute("clientes", clientes);
        model.addAttribute("incluirInativos", incluirInativos);
        model.addAttribute("pesquisa",
                pesquisa == null ? "" : pesquisa);

        return "clientes/lista";
    }

    @GetMapping("/novo")
    public String novo(Model model) {

        prepararFormulario(
                model, new ClienteForm(), false, "/clientes", null);

        return "clientes/formulario";
    }

    @PostMapping
    public String cadastrar(
            @AuthenticationPrincipal Sessao sessao,
            @ModelAttribute ClienteForm clienteForm,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            clienteService.cadastrar(
                    sessao,
                    clienteForm.getNome(),
                    clienteForm.getCpf(),
                    clienteForm.getTelefone(),
                    clienteForm.getEmail());

            redirectAttributes.addFlashAttribute(
                    "sucesso", "Cliente cadastrado com sucesso.");

            return "redirect:/clientes";

        } catch (DadosInvalidosException e) {

            prepararFormulario(
                    model, clienteForm, false, "/clientes", e.getMessage());

            return "clientes/formulario";
        }
    }

    @GetMapping("/{id}/editar")
    public String editar(
            @PathVariable int id,
            @AuthenticationPrincipal Sessao sessao,
            Model model,
            RedirectAttributes redirectAttributes) {

        Optional<Cliente> resultado =
                clienteService.buscarPorId(sessao, id);

        if (resultado.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "erro", "Cliente não encontrado.");

            return "redirect:/clientes";
        }

        Cliente cliente = resultado.get();

        ClienteForm form = new ClienteForm(
                cliente.nome(),
                cliente.cpf(),
                cliente.telefone(),
                cliente.email());

        prepararFormulario(
                model, form, true, "/clientes/" + id, null);

        model.addAttribute("clienteId", id);

        return "clientes/formulario";
    }

    @PostMapping("/{id}")
    public String atualizar(
            @PathVariable int id,
            @AuthenticationPrincipal Sessao sessao,
            @ModelAttribute ClienteForm clienteForm,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            Optional<Cliente> resultado = clienteService.atualizar(
                    sessao,
                    id,
                    clienteForm.getNome(),
                    clienteForm.getCpf(),
                    clienteForm.getTelefone(),
                    clienteForm.getEmail());

            if (resultado.isEmpty()) {
                redirectAttributes.addFlashAttribute(
                        "erro", "Cliente não encontrado.");

                return "redirect:/clientes";
            }

            redirectAttributes.addFlashAttribute(
                    "sucesso", "Cliente atualizado com sucesso.");

            return "redirect:/clientes";

        } catch (DadosInvalidosException e) {

            prepararFormulario(
                    model, clienteForm, true,
                    "/clientes/" + id, e.getMessage());

            model.addAttribute("clienteId", id);

            return "clientes/formulario";
        }
    }

    @PostMapping("/{id}/situacao")
    public String alterarSituacao(
            @PathVariable int id,
            @AuthenticationPrincipal Sessao sessao,
            @RequestParam boolean ativo,
            RedirectAttributes redirectAttributes) {

        boolean alterado =
                clienteService.alterarAtivo(sessao, id, ativo);

        if (alterado) {
            redirectAttributes.addFlashAttribute(
                    "sucesso",
                    ativo ? "Cliente ativado com sucesso."
                          : "Cliente desativado com sucesso.");
        } else {
            redirectAttributes.addFlashAttribute(
                    "erro", "Cliente não encontrado.");
        }

        return "redirect:/clientes";
    }

    private void prepararFormulario(
            Model model,
            ClienteForm form,
            boolean modoEdicao,
            String urlAcao,
            String erro) {

        model.addAttribute("clienteForm", form);
        model.addAttribute("modoEdicao", modoEdicao);
        model.addAttribute("urlAcao", urlAcao);
        model.addAttribute("erro", erro);
    }
}
