package br.unoesc.pixelrent.web;

import java.util.ArrayList;
import java.util.List;

public class LocacaoForm {
    private Integer idCliente;
    private List<Integer> idsMidias = new ArrayList<>();
    private Integer dias = 7;

    public Integer getIdCliente() { return idCliente; }
    public void setIdCliente(Integer idCliente) { this.idCliente = idCliente; }
    public List<Integer> getIdsMidias() { return idsMidias; }
    public void setIdsMidias(List<Integer> idsMidias) {
        this.idsMidias = idsMidias == null ? new ArrayList<>() : new ArrayList<>(idsMidias);
    }
    public Integer getDias() { return dias; }
    public void setDias(Integer dias) { this.dias = dias; }
}
