package br.unoesc.pixelrent.web;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Dados recebidos do formulário de mídias. */
public class MidiaForm {
    private String idJogoPlataforma;
    private String tipo = "FISICA";
    private BigDecimal valorDiaria;
    private String codigoBarras;
    private String estadoConservacao = "BOM";
    private String codigoLicenca;
    private LocalDate validade;

    public String getIdJogoPlataforma() { return idJogoPlataforma; }
    public void setIdJogoPlataforma(String idJogoPlataforma) { this.idJogoPlataforma = idJogoPlataforma; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public BigDecimal getValorDiaria() { return valorDiaria; }
    public void setValorDiaria(BigDecimal valorDiaria) { this.valorDiaria = valorDiaria; }
    public String getCodigoBarras() { return codigoBarras; }
    public void setCodigoBarras(String codigoBarras) { this.codigoBarras = codigoBarras; }
    public String getEstadoConservacao() { return estadoConservacao; }
    public void setEstadoConservacao(String estadoConservacao) { this.estadoConservacao = estadoConservacao; }
    public String getCodigoLicenca() { return codigoLicenca; }
    public void setCodigoLicenca(String codigoLicenca) { this.codigoLicenca = codigoLicenca; }
    public LocalDate getValidade() { return validade; }
    public void setValidade(LocalDate validade) { this.validade = validade; }
}
