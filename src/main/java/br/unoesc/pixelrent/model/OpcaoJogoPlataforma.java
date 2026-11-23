package br.unoesc.pixelrent.model;

/** Par jogo/plataforma permitido pela tabela de associação do catálogo. */
public record OpcaoJogoPlataforma(
        int idJogo,
        String tituloJogo,
        int idPlataforma,
        String nomePlataforma) {

    public String chave() {
        return idJogo + ":" + idPlataforma;
    }

    public String descricao() {
        return tituloJogo + " — " + nomePlataforma;
    }
}
