package br.unoesc.pixelrent.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.unoesc.pixelrent.config.PerfilConexao;
import org.junit.jupiter.api.Test;

class PerfilFuncionarioTest {

    @Test
    void converteOsDoisPerfisExatosDoBanco() {
        assertEquals(PerfilFuncionario.FUNCIONARIO, PerfilFuncionario.deBanco("FUNCIONARIO"));
        assertEquals(PerfilFuncionario.ADMINISTRADOR, PerfilFuncionario.deBanco("ADMINISTRADOR"));
    }

    @Test
    void conversaoIgnoraMaiusculasEMinusculas() {
        assertEquals(PerfilFuncionario.FUNCIONARIO, PerfilFuncionario.deBanco(" funcionario "));
    }

    @Test
    void rejeitaPerfilDesconhecido() {
        assertThrows(IllegalArgumentException.class, () -> PerfilFuncionario.deBanco("GERENTE"));
        assertThrows(IllegalArgumentException.class, () -> PerfilFuncionario.deBanco(null));
    }

    @Test
    void escolhePerfilDeConexaoConformePerfilDeNegocio() {
        assertEquals(PerfilConexao.ATENDENTE, PerfilFuncionario.FUNCIONARIO.perfilConexao());
        assertEquals(PerfilConexao.ADMINISTRADOR, PerfilFuncionario.ADMINISTRADOR.perfilConexao());
    }
}
