package br.unoesc.pixelrent.web;

import br.unoesc.pixelrent.security.PerfilFuncionario;

/** Campos recebidos no cadastro e na edição de funcionários. */
public class FuncionarioForm {
    private String nome;
    private String cpf;
    private String telefone;
    private String email;
    private String login;
    private String senha;
    private String confirmarSenha;
    private PerfilFuncionario perfil = PerfilFuncionario.FUNCIONARIO;

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getCpf() { return cpf; }
    public void setCpf(String cpf) { this.cpf = cpf; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }
    public String getConfirmarSenha() { return confirmarSenha; }
    public void setConfirmarSenha(String confirmarSenha) { this.confirmarSenha = confirmarSenha; }
    public PerfilFuncionario getPerfil() { return perfil; }
    public void setPerfil(PerfilFuncionario perfil) { this.perfil = perfil; }
}
