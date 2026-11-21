package br.unoesc.pixelrent.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.unoesc.pixelrent.exception.AcessoNegadoException;
import org.junit.jupiter.api.Test;

class PoliticaAcessoTest {

    private final PoliticaAcesso politica = new PoliticaAcesso();

    @Test
    void funcionarioVeOperacoesDeAtendimentoMasNaoAreasAdministrativas() {
        PerfilFuncionario perfil = PerfilFuncionario.FUNCIONARIO;
        assertTrue(politica.podeAcessar(perfil, Permissao.CLIENTES));
        assertTrue(politica.podeAcessar(perfil, Permissao.LOCACOES));
        assertTrue(politica.podeAcessar(perfil, Permissao.DEVOLUCOES));
        assertTrue(politica.podeAcessar(perfil, Permissao.RELATORIO_JOGOS_MAIS_ALUGADOS));
        assertFalse(politica.podeAcessar(perfil, Permissao.FUNCIONARIOS));
        assertFalse(politica.podeAcessar(perfil, Permissao.RELATORIO_FATURAMENTO_MENSAL));
        assertFalse(politica.podeAcessar(perfil, Permissao.AUDITORIA));
    }

    @Test
    void administradorPossuiTodasAsPermissoesDaInterface() {
        for (Permissao permissao : Permissao.values()) {
            assertTrue(politica.podeAcessar(PerfilFuncionario.ADMINISTRADOR, permissao));
        }
    }

    @Test
    void exigirPermissaoPermiteOuBloqueiaConformePerfil() {
        Sessao funcionario = new Sessao(10, "Julia", PerfilFuncionario.FUNCIONARIO);
        Sessao administrador = new Sessao(20, "Marcos", PerfilFuncionario.ADMINISTRADOR);

        assertDoesNotThrow(() -> politica.exigir(funcionario, Permissao.CLIENTES));
        assertThrows(AcessoNegadoException.class,
                () -> politica.exigir(funcionario, Permissao.AUDITORIA));
        assertDoesNotThrow(() -> politica.exigir(administrador, Permissao.AUDITORIA));
    }
}
