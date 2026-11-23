package br.unoesc.pixelrent.web;

/** Campo comum dos formulários de gêneros e plataformas. */
public class NomeCadastroForm {

    private String nome;

    public NomeCadastroForm() {
    }

    public NomeCadastroForm(String nome) {
        this.nome = nome;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }
}
