package br.unoesc.pixelrent.security;

import br.unoesc.pixelrent.exception.AcessoNegadoException;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Política centralizada de acesso às funcionalidades da interface e dos services. */
public final class PoliticaAcesso {

    private static final Set<Permissao> PERMISSOES_FUNCIONARIO = Collections.unmodifiableSet(
            EnumSet.of(
                    Permissao.CLIENTES,
                    Permissao.LOCACOES,
                    Permissao.DEVOLUCOES,
                    Permissao.CONSULTAS,
                    Permissao.JOGOS,
                    Permissao.RELATORIO_JOGOS_MAIS_ALUGADOS));

    private static final Set<Permissao> PERMISSOES_ADMINISTRADOR = Collections.unmodifiableSet(
            EnumSet.allOf(Permissao.class));

    /** Verifica se o perfil tem acesso à funcionalidade. */
    public boolean podeAcessar(PerfilFuncionario perfil, Permissao permissao) {
        if (perfil == null || permissao == null) {
            return false;
        }
        return permissoesPara(perfil).contains(permissao);
    }

    /** Retorna uma coleção imutável de permissões do perfil informado. */
    public Set<Permissao> permissoesPara(PerfilFuncionario perfil) {
        if (perfil == null) {
            return Set.of();
        }
        return perfil == PerfilFuncionario.ADMINISTRADOR
                ? PERMISSOES_ADMINISTRADOR : PERMISSOES_FUNCIONARIO;
    }

    /** Impede que uma chamada a service prossiga sem a permissão necessária. */
    public void exigir(Sessao sessao, Permissao permissao) {
        if (sessao == null || !podeAcessar(sessao.perfil(), permissao)) {
            throw new AcessoNegadoException("Seu perfil não tem permissão para esta operação.");
        }
    }
}
