package br.estudos.app.ai;

import br.estudos.app.ports.AiRunner;
import br.estudos.app.tasks.CancellationToken;
import br.estudos.app.tasks.TaskHandle;
import br.estudos.app.tasks.TaskQueue;
import br.estudos.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.IntSupplier;

/** Único ponto que chama um {@link AiRunner}: aplica cache, orçamento diário e grava em {@code ai_call}. */
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final Map<AiRunnerKind, AiRunner> runners = new EnumMap<>(AiRunnerKind.class);
    private final AiCallRepository repo;
    private final TaskQueue queue;
    private final Clock clock;
    private final IntSupplier dailyLimit;

    public AiService(List<AiRunner> runners, AiCallRepository repo, TaskQueue queue,
                     Clock clock, IntSupplier dailyLimit) {
        runners.forEach(r -> this.runners.put(r.kind(), r));
        this.repo = repo;
        this.queue = queue;
        this.clock = clock;
        this.dailyLimit = dailyLimit;
    }

    public boolean supported(AiRunnerKind kind) {
        AiRunner r = runners.get(kind);
        return r != null && r.supported();
    }

    /** Envia para a fila de IA. {@code refresh = true} é o "Gerar de novo": ignora o cache. */
    public TaskHandle<AiOutcome> submit(AiRequest req, boolean refresh, BudgetGate gate) {
        String title = req.task() + " · " + req.runner() + (req.model() == null ? "" : " " + req.model());
        return queue.submitAi(title, tok -> execute(req, refresh, gate, tok));
    }

    /** Versão síncrona de {@link #submit}; bloqueia a thread atual. */
    public AiOutcome execute(AiRequest req, boolean refresh, BudgetGate gate, CancellationToken tok)
            throws AiException, IOException {
        String key = CacheKeys.of(req);
        if (!refresh) {
            var hit = repo.findOk(key);
            if (hit.isPresent()) {
                AiCall row = repo.insert(new AiCall(0, key, req.runner(), req.model(), req.task(), req.prompt(),
                        hit.get().response(), AiCallStatus.OK, null, null, null, 0, now(), true));
                return new AiOutcome(row, true);
            }
        }
        AiRunner runner = runners.get(req.runner());
        if (runner == null || !runner.supported())
            throw new UnsupportedOperationException("A CLI " + req.runner() + " não tem modo não interativo disponível.");

        BudgetStatus budget = budget();
        if (budget.exceeded() && !gate.confirm(budget)) throw new BudgetDeclinedException(budget);
        tok.throwIfCancelled();

        long t0 = System.nanoTime();
        try {
            AiResult r = runner.run(req, tok);
            tok.throwIfCancelled();
            AiCall row = record(key, req, r.response(), AiCallStatus.OK, r, t0);
            return new AiOutcome(row, false);
        } catch (CancellationException e) {
            Thread.interrupted(); // limpa a interrupção antes de gravar
            record(key, req, null, AiCallStatus.CANCELADO, null, t0);
            throw e;
        } catch (AiException | RuntimeException e) {
            if (tok.isCancelled()) {
                Thread.interrupted();
                record(key, req, null, AiCallStatus.CANCELADO, null, t0);
                throw new CancellationException("cancelado");
            }
            log.warn("Chamada de IA falhou ({} {}): {}", req.runner(), req.task(), e.getMessage());
            record(key, req, e.getMessage(), AiCallStatus.ERRO, null, t0);
            throw e;
        }
    }

    /** Uso de hoje (dia local do relógio). */
    public BudgetStatus budget() {
        return repo.usageSince(startOfToday(), dailyLimit.getAsInt());
    }

    /** Estatística dos últimos {@code days} dias (inclui hoje). */
    public AiUsage usage(int days) {
        Instant from = LocalDate.now(clock).minusDays(days - 1L).atStartOfDay(clock.getZone()).toInstant();
        return AiUsage.of(repo.statsSince(from), clock.getZone());
    }

    private AiCall record(String key, AiRequest req, String response, AiCallStatus status, AiResult r, long t0) {
        long ms = Duration.ofNanos(System.nanoTime() - t0).toMillis();
        return repo.insert(new AiCall(0, key, req.runner(), req.model(), req.task(), req.prompt(), response, status,
                r == null ? null : r.inputTokens(), r == null ? null : r.outputTokens(),
                r == null ? null : r.costUsd(), ms, now(), false));
    }

    private Instant startOfToday() {
        return LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }
}
