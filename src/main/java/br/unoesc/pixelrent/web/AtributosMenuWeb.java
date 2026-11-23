package br.unoesc.pixelrent.web;

import br.unoesc.pixelrent.security.PoliticaAcesso;
import br.unoesc.pixelrent.security.Sessao;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Disponibiliza as permissões do usuário autenticado em todas as páginas Thymeleaf. */
@ControllerAdvice(annotations = Controller.class)
public class AtributosMenuWeb {

    private final PoliticaAcesso politicaAcesso;

    public AtributosMenuWeb(PoliticaAcesso politicaAcesso) {
        this.politicaAcesso = politicaAcesso;
    }

    @ModelAttribute("permissoes")
    public Set<String> adicionarPermissoes(@AuthenticationPrincipal Sessao sessao) {
        if (sessao == null) {
            return Set.of();
        }
        return politicaAcesso.permissoesPara(sessao.perfil()).stream()
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());
    }
}
