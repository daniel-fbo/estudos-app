package br.estudos.app.ai;

import br.estudos.app.tasks.CancellationToken;
import br.estudos.app.tasks.TaskQueue;
import br.estudos.app.tasks.TaskState;
import br.estudos.domain.*;
import br.estudos.infra.ai.JdbcAiCallRepository;
import br.estudos.infra.db.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AiServiceTest {
    @TempDir Path dir;

    static class MutableClock extends Clock {
        Instant now;
        MutableClock(Instant i) { now = i; }
        public ZoneId getZone() { return ZoneId.of("UTC"); }
        public Clock withZone(ZoneId z) { return this; }
        public Instant instant() { return now; }
    }

    MutableClock clock = new MutableClock(Instant.parse("2026-11-07T10:00:00Z"));
    FakeAiRunner claude = new FakeAiRunner(AiRunnerKind.CLAUDE);
    TaskQueue queue;
    JdbcAiCallRepository repo;
    AiService svc;
    int limit = 2;

    @BeforeEach
    void setUp() throws Exception {
        Database db = new Database(dir.resolve("ai.db"));
        db.migrate();
        repo = new JdbcAiCallRepository(db);
        queue = new TaskQueue(clock);
        svc = new AiService(List.of(claude), repo, queue, clock, () -> limit);
    }

    @AfterEach
    void tearDown() { queue.close(); }

    AiRequest req(String prompt) { return AiRequest.of(AiRunnerKind.CLAUDE, "haiku", AiTask.OUTRO, prompt); }

    AiOutcome run(AiRequest r, boolean refresh, BudgetGate g) throws Exception {
        return svc.execute(r, refresh, g, CancellationToken.none());
    }

    @Test
    void cacheReturnsStoredResultAndRefreshIgnoresIt() throws Exception {
        AiOutcome first = run(req("oi"), false, BudgetGate.NEVER);
        assertFalse(first.fromCache());
        assertEquals("eco: oi", first.response());
        assertEquals(15, first.call().totalTokens());

        AiOutcome second = run(req("oi"), false, BudgetGate.NEVER);
        assertTrue(second.fromCache());
        assertEquals("eco: oi", second.response());
        assertEquals(1, claude.calls.get());

        // outro modelo / tarefa → outra chave
        run(AiRequest.of(AiRunnerKind.CLAUDE, "sonnet", AiTask.OUTRO, "oi"), false, BudgetGate.ALWAYS);
        assertEquals(2, claude.calls.get());

        // "Gerar de novo"
        AiOutcome again = run(req("oi"), true, BudgetGate.ALWAYS);
        assertFalse(again.fromCache());
        assertEquals(3, claude.calls.get());

        AiUsage u = svc.usage(7);
        assertEquals(1, u.cacheHits());
        assertEquals(3, u.realCalls());
        assertEquals(0.25, u.cacheHitRate(), 1e-9);
        assertEquals(3, u.byModel().get("CLAUDE haiku").calls() + u.byModel().get("CLAUDE sonnet").calls());
        assertEquals(0.03, u.byTask().get("OUTRO").costUsd(), 1e-9);
    }

    @Test
    void attachmentContentIsPartOfTheKey() throws Exception {
        Path f = dir.resolve("a.txt");
        Files.writeString(f, "v1");
        AiRequest r = req("resuma").withAttachments(List.of(f));
        String k1 = CacheKeys.of(r);
        run(r, false, BudgetGate.ALWAYS);
        assertTrue(run(r, false, BudgetGate.ALWAYS).fromCache());
        Files.writeString(f, "v2");
        assertNotEquals(k1, CacheKeys.of(r));
        assertFalse(run(r, false, BudgetGate.ALWAYS).fromCache());
    }

    @Test
    void errorsAreRecordedButNeverCached() throws Exception {
        claude.failWith = "quebrou";
        assertThrows(AiException.class, () -> run(req("x"), false, BudgetGate.ALWAYS));
        claude.failWith = null;
        assertFalse(run(req("x"), false, BudgetGate.ALWAYS).fromCache());
        assertEquals(2, claude.calls.get());
    }

    @Test
    void budgetAsksForConfirmationOnlyWhenExceeded() throws Exception {
        AtomicInteger asked = new AtomicInteger();
        BudgetGate counting = s -> { asked.incrementAndGet(); return false; };
        run(req("1"), false, counting);
        run(req("2"), false, counting);
        assertEquals(0, asked.get());
        assertEquals(2, svc.budget().callsToday());
        assertEquals(30, svc.budget().tokensToday());

        assertThrows(BudgetDeclinedException.class, () -> run(req("3"), false, counting));
        assertEquals(1, asked.get());
        assertEquals(2, claude.calls.get());

        // cache não passa pelo orçamento
        assertTrue(run(req("1"), false, counting).fromCache());
        assertEquals(1, asked.get());

        // confirmando, chama
        assertEquals("eco: 3", run(req("3"), false, BudgetGate.ALWAYS).response());
        assertEquals(3, svc.budget().callsToday());

        // dia seguinte zera
        clock.now = clock.now.plus(Duration.ofDays(1));
        assertEquals(0, svc.budget().callsToday());
        run(req("4"), false, counting);
        assertEquals(1, asked.get());
    }

    @Test
    void cancellingARunningCallRecordsCancelledAndIsNotCached() throws Exception {
        claude.block = true;
        var h = svc.submit(req("longo"), false, BudgetGate.ALWAYS);
        assertTrue(claude.started.await(5, TimeUnit.SECONDS));
        h.cancel();
        assertThrows(CancellationException.class, () -> h.result().get(5, TimeUnit.SECONDS));
        waitUntil(() -> queue.snapshot().getFirst().state() == TaskState.CANCELADA);
        waitUntil(() -> repo.statsSince(Instant.EPOCH).size() == 1);
        assertEquals(AiCallStatus.CANCELADO, repo.statsSince(Instant.EPOCH).getFirst().status());

        claude.block = false;
        AiOutcome o = svc.submit(req("longo"), false, BudgetGate.ALWAYS).result().get(5, TimeUnit.SECONDS);
        assertFalse(o.fromCache());
        assertEquals(2, claude.calls.get());
    }

    @Test
    void unsupportedRunnerFailsClearly() {
        var e = assertThrows(UnsupportedOperationException.class,
                () -> run(AiRequest.of(AiRunnerKind.AGY, null, AiTask.OUTRO, "x"), false, BudgetGate.ALWAYS));
        assertTrue(e.getMessage().contains("AGY"));
        assertFalse(svc.supported(AiRunnerKind.AGY));
        assertTrue(svc.supported(AiRunnerKind.CLAUDE));
    }

    static void waitUntil(java.util.function.BooleanSupplier c) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("condição não atingida");
            Thread.sleep(10);
        }
    }
}
