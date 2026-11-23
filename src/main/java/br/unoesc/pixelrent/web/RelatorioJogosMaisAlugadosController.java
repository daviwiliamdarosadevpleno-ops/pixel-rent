package br.unoesc.pixelrent.web;

import br.unoesc.pixelrent.security.Sessao;
import br.unoesc.pixelrent.service.RelatorioService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/relatorios/jogos-mais-alugados")
public class RelatorioJogosMaisAlugadosController {
    private final RelatorioService service;

    public RelatorioJogosMaisAlugadosController(RelatorioService service) { this.service = service; }

    @GetMapping
    public String listar(@AuthenticationPrincipal Sessao sessao, Model model) {
        var jogos = service.listarJogosMaisAlugados(sessao);
        model.addAttribute("jogos", jogos);
        model.addAttribute("totalJogos", jogos.size());
        model.addAttribute("totalLocacoes", jogos.stream().mapToLong(j -> j.vezesAlugado()).sum());
        return "relatorios/jogos-mais-alugados";
    }
}
