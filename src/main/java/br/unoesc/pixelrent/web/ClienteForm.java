
package br.unoesc.pixelrent.web;

/**
 * Dados recebidos dos formulários de cadastro e edição de clientes.
 */
public class ClienteForm {

    private String nome;
    private String cpf;
    private String telefone;
    private String email;

    public ClienteForm() {
    }

    public ClienteForm(
            String nome,
            String cpf,
            String telefone,
            String email) {

        this.nome = nome;
        this.cpf = cpf;
        this.telefone = telefone;
        this.email = email;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
