package br.unoesc.pixelrent.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.unoesc.pixelrent.exception.AcessoNegadoException;
import org.junit.jupiter.api.Test;

class ContextoSessaoTest {

    private Sessao sessaoFuncionario() {
        return new Sessao(10, "Julia", PerfilFuncionario.FUNCIONARIO);
    }

    @Test
    void iniciaVazioEExigeLogin() {
        ContextoSessao contexto = new ContextoSessao();
        assertTrue(contexto.sessaoAtual().isEmpty());
        assertThrows(AcessoNegadoException.class, contexto::exigirSessao);
    }

    @Test
    void iniciaERecuperaSessao() {
        ContextoSessao contexto = new ContextoSessao();
        Sessao sessao = sessaoFuncionario();
        contexto.iniciar(sessao);
        assertEquals(sessao, contexto.exigirSessao());
        assertEquals(sessao, contexto.sessaoAtual().orElseThrow());
    }

    @Test
    void naoSubstituiSessaoAtivaSilenciosamente() {
        ContextoSessao contexto = new ContextoSessao();
        contexto.iniciar(sessaoFuncionario());
        assertThrows(IllegalStateException.class,
                () -> contexto.iniciar(new Sessao(11, "Marcos", PerfilFuncionario.ADMINISTRADOR)));
    }

    @Test
    void encerraSessao() {
        ContextoSessao contexto = new ContextoSessao();
        contexto.iniciar(sessaoFuncionario());
        contexto.encerrar();
        contexto.encerrar(); // idempotente
        assertFalse(contexto.sessaoAtual().isPresent());
    }
}
