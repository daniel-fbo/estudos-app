package br.estudos.app.tasks;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;

/** Sinal de cancelamento cooperativo. Os callbacks de {@link #onCancel} rodam uma vez, na thread que cancelou. */
public final class CancellationToken {

    private volatile boolean cancelled;
    private final List<Runnable> callbacks = new CopyOnWriteArrayList<>();

    public static CancellationToken none() {
        return new CancellationToken();
    }

    public void cancel() {
        if (cancelled) return;
        cancelled = true;
        callbacks.forEach(Runnable::run);
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void throwIfCancelled() {
        if (cancelled) throw new CancellationException("cancelado");
    }

    /** Registra um callback; se já estiver cancelado, roda na hora. */
    public void onCancel(Runnable r) {
        callbacks.add(r);
        if (cancelled) r.run();
    }
}
