package br.estudos.app.tasks;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TaskQueueTest {

    @Test
    void runsReportsAndCancels() throws Exception {
        try (TaskQueue q = new TaskQueue(Clock.systemUTC())) {
            AtomicInteger events = new AtomicInteger();
            q.addListener(events::incrementAndGet);

            assertEquals(42, q.submit("ok", t -> 42).result().get(5, TimeUnit.SECONDS));

            var failed = q.submitAi("falha", t -> { throw new IllegalStateException("boom"); });
            var ex = assertThrows(ExecutionException.class, () -> failed.result().get(5, TimeUnit.SECONDS));
            assertEquals("boom", ex.getCause().getMessage());

            CountDownLatch started = new CountDownLatch(1);
            var slow = q.submitAi("lenta", t -> { started.countDown(); Thread.sleep(60_000); return 1; });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            q.cancel(slow.id());
            assertThrows(CancellationException.class, () -> slow.result().get(5, TimeUnit.SECONDS));

            long end = System.currentTimeMillis() + 5000;
            while (q.snapshot().getFirst().state() != TaskState.CANCELADA && System.currentTimeMillis() < end)
                Thread.sleep(10);
            var snap = q.snapshot();
            assertEquals(TaskState.CANCELADA, snap.get(0).state());
            assertEquals(TaskState.FALHOU, snap.get(1).state());
            assertEquals("boom", snap.get(1).message());
            assertEquals(TaskState.CONCLUIDA, snap.get(2).state());
            assertTrue(snap.get(0).ai());
            assertTrue(events.get() >= 9);
        }
    }

    @Test
    void cancellingAQueuedTaskNeverRunsIt() throws Exception {
        try (TaskQueue q = new TaskQueue(Clock.systemUTC())) {
            CountDownLatch release = new CountDownLatch(1);
            q.submitAi("a", t -> { release.await(); return 0; });
            q.submitAi("b", t -> { release.await(); return 0; });
            AtomicInteger ran = new AtomicInteger();
            var queued = q.submitAi("c", t -> ran.incrementAndGet());
            queued.cancel();
            release.countDown();
            assertTrue(queued.result().isCancelled());
            Thread.sleep(100);
            assertEquals(0, ran.get());
            assertEquals(TaskState.CANCELADA, q.snapshot().getFirst().state());
        }
    }

    @Test
    void trimsHistoryKeepingOnlyLastFiftyFinishedTasks() throws Exception {
        try (TaskQueue q = new TaskQueue(Clock.systemUTC())) {
            for (int i = 0; i < 60; i++) {
                int val = i;
                q.submit("t" + i, t -> val).result().get(5, TimeUnit.SECONDS);
            }
            var snap = q.snapshot();
            assertEquals(50, snap.size());
            assertEquals("t59", snap.getFirst().title());
            assertEquals("t10", snap.getLast().title());
        }
    }

    @Test
    void cancellingFinishedTaskHandleDoesNotAlterCompletedState() throws Exception {
        try (TaskQueue q = new TaskQueue(Clock.systemUTC())) {
            var handle = q.submit("concluida", t -> "ok");
            assertEquals("ok", handle.result().get(5, TimeUnit.SECONDS));
            assertEquals(TaskState.CONCLUIDA, q.snapshot().getFirst().state());

            handle.cancel();

            assertEquals(TaskState.CONCLUIDA, q.snapshot().getFirst().state());
            assertFalse(handle.result().isCancelled());
        }
    }

    @Test
    void submitGeneralPoolRunsTaskAndMarksAiFalse() throws Exception {
        try (TaskQueue q = new TaskQueue(Clock.systemUTC())) {
            var handle = q.submit("geral", t -> 123);
            assertEquals(123, handle.result().get(5, TimeUnit.SECONDS));
            var info = q.snapshot().getFirst();
            assertFalse(info.ai());
            assertEquals("geral", info.title());
            assertEquals(TaskState.CONCLUIDA, info.state());
        }
    }
}
