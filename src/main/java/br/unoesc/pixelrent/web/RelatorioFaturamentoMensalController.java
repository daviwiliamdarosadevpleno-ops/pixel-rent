package br.unoesc.pixelrent.web;

import br.unoesc.pixelrent.security.Sessao;
import br.unoesc.pixelrent.service.RelatorioService;

import java.math.BigDecimal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/relatorios/faturamento-mensal")
public class RelatorioFaturamentoMensalController {
    private final RelatorioService service;

    public RelatorioFaturamentoMensalController(RelatorioService service) { this.service = service; }

    @GetMapping
    public String listar(@AuthenticationPrincipal Sessao sessao, Model model) {
        var meses = service.listarFaturamentoMensal(sessao);
        BigDecimal total = meses.stream().map(m -> m.faturamento()).reduce(BigDecimal.ZERO, BigDecimal::add);
        long locacoes = meses.stream().mapToLong(m -> m.quantidadeLocacoes()).sum();
        model.addAttribute("meses", meses);
        model.addAttribute("faturamentoTotal", total);
        model.addAttribute("totalLocacoes", locacoes);
        model.addAttribute("quantidadeMeses", meses.size());
        return "relatorios/faturamento-mensal";
    }
}
