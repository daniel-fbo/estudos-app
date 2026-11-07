package br.estudos.app.tasks;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fila de tarefas em segundo plano: 2 threads para IA, 2 para o resto. Observável via {@link #addListener}
 * (o listener roda na thread que mudou o estado; a UI deve repassar com {@code Platform.runLater}).
 */
public final class TaskQueue implements AutoCloseable {

    @FunctionalInterface
    public interface Work<T> {
        T run(CancellationToken token) throws Exception;
    }

    /** Quantas tarefas terminadas ficam no histórico. */
    static final int HISTORY = 50;

    private final ExecutorService aiPool;
    private final ExecutorService generalPool;
    private final Clock clock;
    private final AtomicLong ids = new AtomicLong();
    private final Map<Long, TaskInfo> tasks = new LinkedHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Map<Long, Runnable> cancellers = new ConcurrentHashMap<>();

    public TaskQueue(Clock clock) {
        this.clock = clock;
        this.aiPool = Executors.newFixedThreadPool(2, daemon("estudos-ia"));
        this.generalPool = Executors.newFixedThreadPool(2, daemon("estudos-bg"));
    }

    /** Executor para trabalho curto que não precisa aparecer no painel. */
    public ExecutorService generalExecutor() {
        return generalPool;
    }

    public <T> TaskHandle<T> submitAi(String title, Work<T> work) {
        return submit(aiPool, true, title, work);
    }

    public <T> TaskHandle<T> submit(String title, Work<T> work) {
        return submit(generalPool, false, title, work);
    }

    public synchronized List<TaskInfo> snapshot() {
        List<TaskInfo> l = new ArrayList<>(tasks.values());
        java.util.Collections.reverse(l);
        return l;
    }

    public void addListener(Runnable l) { listeners.add(l); }

    public void removeListener(Runnable l) { listeners.remove(l); }

    private <T> TaskHandle<T> submit(ExecutorService pool, boolean ai, String title, Work<T> work) {
        long id = ids.incrementAndGet();
        CancellationToken token = new CancellationToken();
        CompletableFuture<T> result = new CompletableFuture<>();
        update(new TaskInfo(id, title, ai, TaskState.NA_FILA, now(), null, null));
        Future<?> f = pool.submit(() -> {
            if (token.isCancelled()) return;
            setState(id, TaskState.RODANDO, null);
            try {
                T r = work.run(token);
                if (token.isCancelled()) throw new CancellationException("cancelado");
                setState(id, TaskState.CONCLUIDA, null);
                result.complete(r);
            } catch (CancellationException e) {
                setState(id, TaskState.CANCELADA, null);
                result.cancel(false);
            } catch (Throwable t) {
                if (token.isCancelled()) {
                    setState(id, TaskState.CANCELADA, null);
                    result.cancel(false);
                } else {
                    setState(id, TaskState.FALHOU, t.getMessage() == null ? t.toString() : t.getMessage());
                    result.completeExceptionally(t);
                }
            }
        });
        Runnable canceller = () -> {
            TaskState cur = state(id);
            if (cur != null && cur.finished()) return;
            boolean wasQueued = cur == TaskState.NA_FILA;
            token.cancel();
            f.cancel(true);
            if (wasQueued) {
                setState(id, TaskState.CANCELADA, null);
                result.cancel(false);
            }
        };
        cancellers.put(id, canceller);
        if (state(id).finished()) cancellers.remove(id);
        return new TaskHandle<>(id, result, canceller);
    }

    /** Cancela a tarefa {@code id}, se ainda não terminou. */
    public void cancel(long id) {
        Runnable c = cancellers.get(id);
        if (c != null) c.run();
    }

    private synchronized TaskState state(long id) {
        TaskInfo i = tasks.get(id);
        return i == null ? null : i.state();
    }

    private void setState(long id, TaskState s, String msg) {
        synchronized (this) {
            TaskInfo i = tasks.get(id);
            if (i == null || i.state().finished()) return;
            tasks.put(id, new TaskInfo(id, i.title(), i.ai(), s, i.submittedAt(),
                    s.finished() ? now() : null, msg));
            if (s.finished()) cancellers.remove(id);
            trimHistory();
        }
        fire();
    }

    private void update(TaskInfo info) {
        synchronized (this) {
            tasks.put(info.id(), info);
        }
        fire();
    }

    private void trimHistory() {
        long done = tasks.values().stream().filter(t -> t.state().finished()).count();
        var it = tasks.values().iterator();
        while (done > HISTORY && it.hasNext()) {
            if (it.next().state().finished()) {
                it.remove();
                done--;
            }
        }
    }

    private void fire() {
        listeners.forEach(Runnable::run);
    }

    private Instant now() {
        return clock.instant();
    }

    private static ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    @Override
    public void close() {
        aiPool.shutdownNow();
        generalPool.shutdownNow();
        try {
            aiPool.awaitTermination(2, TimeUnit.SECONDS);
            generalPool.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
