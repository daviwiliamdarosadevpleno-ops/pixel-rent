package br.unoesc.pixelrent.web;

import java.util.ArrayList;
import java.util.List;

/** Dados recebidos do formulário de jogos. */
public class JogoForm {
    private String titulo;
    private Integer anoLancamento;
    private String classificacaoIndicativa = "LIVRE";
    private Integer idGenero;
    private List<Integer> plataformas = new ArrayList<>();

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public Integer getAnoLancamento() { return anoLancamento; }
    public void setAnoLancamento(Integer anoLancamento) { this.anoLancamento = anoLancamento; }
    public String getClassificacaoIndicativa() { return classificacaoIndicativa; }
    public void setClassificacaoIndicativa(String classificacaoIndicativa) { this.classificacaoIndicativa = classificacaoIndicativa; }
    public Integer getIdGenero() { return idGenero; }
    public void setIdGenero(Integer idGenero) { this.idGenero = idGenero; }
    public List<Integer> getPlataformas() { return plataformas; }
    public void setPlataformas(List<Integer> plataformas) { this.plataformas = plataformas == null ? new ArrayList<>() : new ArrayList<>(plataformas); }
}
