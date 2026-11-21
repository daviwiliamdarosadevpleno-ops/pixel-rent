package br.unoesc.pixelrent.security;

import br.unoesc.pixelrent.exception.AcessoNegadoException;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mantém a sessão atual de forma segura entre threads.
 * A instância é criada pela aplicação e injetada nas telas; não usa estado global estático.
 */
public final class ContextoSessao {

    private final AtomicReference<Sessao> sessaoAtual = new AtomicReference<>();

    /** Inicia uma sessão; não permite substituir uma sessão ativa silenciosamente. */
    public void iniciar(Sessao novaSessao) {
        if (novaSessao == null) {
            throw new IllegalArgumentException("A sessão não pode ser nula.");
        }
        if (!sessaoAtual.compareAndSet(null, novaSessao)) {
            throw new IllegalStateException("Já existe uma sessão ativa.");
        }
    }

    /** @return sessão atual, se houver */
    public Optional<Sessao> sessaoAtual() {
        return Optional.ofNullable(sessaoAtual.get());
    }

    /** @return sessão atual; falha quando não há funcionário autenticado */
    public Sessao exigirSessao() {
        Sessao sessao = sessaoAtual.get();
        if (sessao == null) {
            throw new AcessoNegadoException("É necessário autenticar um funcionário.");
        }
        return sessao;
    }

    /** Encerra a sessão atual. É seguro chamar mesmo que já esteja encerrada. */
    public void encerrar() {
        sessaoAtual.set(null);
    }
}
