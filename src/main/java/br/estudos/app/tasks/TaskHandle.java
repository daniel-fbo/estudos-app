package br.estudos.app.tasks;

import java.util.concurrent.CompletableFuture;

/** Referência a uma tarefa enviada: resultado assíncrono e cancelamento. */
public final class TaskHandle<T> {
    private final long id;
    private final CompletableFuture<T> result;
    private final Runnable canceller;

    TaskHandle(long id, CompletableFuture<T> result, Runnable canceller) {
        this.id = id;
        this.result = result;
        this.canceller = canceller;
    }

    public long id() { return id; }

    /** Completa com o valor, com a exceção da tarefa, ou cancelado. */
    public CompletableFuture<T> result() { return result; }

    public void cancel() { canceller.run(); }
}
