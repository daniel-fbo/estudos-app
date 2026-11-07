package br.estudos.app.ai;

import br.estudos.app.ports.AiRunner;
import br.estudos.app.tasks.CancellationToken;
import br.estudos.domain.AiRequest;
import br.estudos.domain.AiResult;
import br.estudos.domain.AiRunnerKind;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** Runner falso: responde "eco: prompt", conta chamadas e pode bloquear até ser cancelado ou falhar. */
public class FakeAiRunner implements AiRunner {
    final AiRunnerKind kind;
    final AtomicInteger calls = new AtomicInteger();
    volatile boolean block;
    volatile String failWith;
    final CountDownLatch started = new CountDownLatch(1);

    public FakeAiRunner(AiRunnerKind kind) { this.kind = kind; }

    @Override public AiRunnerKind kind() { return kind; }

    @Override
    public AiResult run(AiRequest req, CancellationToken token) throws AiException {
        calls.incrementAndGet();
        started.countDown();
        if (block) {
            CountDownLatch done = new CountDownLatch(1);
            token.onCancel(done::countDown);
            try {
                done.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            token.throwIfCancelled();
        }
        if (failWith != null) throw new AiException(failWith);
        return new AiResult("eco: " + req.prompt(), 10L, 5L, 0.01);
    }
}
