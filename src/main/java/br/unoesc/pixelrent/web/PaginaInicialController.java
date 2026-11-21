package br.unoesc.pixelrent.web;

import br.unoesc.pixelrent.security.PoliticaAcesso;
import br.unoesc.pixelrent.security.Sessao;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PaginaInicialController {

    private final PoliticaAcesso politicaAcesso;

    public PaginaInicialController(PoliticaAcesso politicaAcesso) {
        this.politicaAcesso = politicaAcesso;
    }

    @GetMapping("/")
    public String paginaInicial(
            @AuthenticationPrincipal Sessao sessao,
            Model model) {

        model.addAttribute("sessao", sessao);

        Set<String> permissoes = sessao == null
                ? Set.of()
                : politicaAcesso.permissoesPara(sessao.perfil())
                        .stream()
                        .map(Enum::name)
                        .collect(Collectors.toUnmodifiableSet());

        model.addAttribute("permissoes", permissoes);

        return "index";
    }
}