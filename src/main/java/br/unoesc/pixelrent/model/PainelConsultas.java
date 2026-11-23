package br.unoesc.pixelrent.model;

import java.util.List;

public record PainelConsultas(
        List<ItemLocacaoAtiva> locacoesAtivas,
        List<LocacaoAtrasada> locacoesAtrasadas,
        List<ItemHistoricoLocacao> historico,
        int midiasDisponiveis) {
    public PainelConsultas {
        locacoesAtivas = List.copyOf(locacoesAtivas);
        locacoesAtrasadas = List.copyOf(locacoesAtrasadas);
        historico = List.copyOf(historico);
    }
}
