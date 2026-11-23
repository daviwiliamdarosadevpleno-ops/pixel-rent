package br.unoesc.pixelrent.web;

import br.unoesc.pixelrent.security.PoliticaAcesso;
import br.unoesc.pixelrent.security.Sessao;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Disponibiliza as permissões do usuário ao menu compartilhado em todas as páginas. */
@ControllerAdvice(annotations = Controller.class)
public class PermissoesMenuWeb {
    private final PoliticaAcesso politicaAcesso;

    public PermissoesMenuWeb(PoliticaAcesso politicaAcesso) {
        this.politicaAcesso = politicaAcesso;
    }

    @ModelAttribute
    public void adicionarPermissoes(Model model) {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        Object principal = autenticacao == null ? null : autenticacao.getPrincipal();
        Set<String> permissoes = principal instanceof Sessao sessao
                ? politicaAcesso.permissoesPara(sessao.perfil()).stream()
                    .map(Enum::name).collect(Collectors.toUnmodifiableSet())
                : Set.of();
        model.addAttribute("permissoes", permissoes);
    }
}
