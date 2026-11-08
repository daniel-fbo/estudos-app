package br.estudos.app.lessons;

import br.estudos.app.ai.AiService;
import br.estudos.app.ai.BudgetGate;
import br.estudos.app.config.Config;
import br.estudos.app.tasks.TaskHandle;
import br.estudos.domain.*;
import br.estudos.infra.config.AppPaths;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Gera aulas pela skill (via {@link AiService}), recolhe os arquivos e registra em {@code lesson}. */
public class LessonService {
    public static final String KIND_MATH = "aula_matematica";
    public static final String KIND_PROG = "aula_programacao";
    public static final double SIMILAR_THRESHOLD = 0.5;
    static final Duration TIMEOUT = Duration.ofMinutes(60);

    private final AiService ai;
    private final LessonRepository repo;
    private final Supplier<Config> config;
    private final Clock clock;
    private final java.util.Set<Long> queued = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "aulas");
        t.setDaemon(true);
        return t;
    });

    public LessonService(AiService ai, LessonRepository repo, Supplier<Config> config, Clock clock) {
        this.ai = ai;
        this.repo = repo;
        this.config = config;
        this.clock = clock;
    }

    public static String areaOf(String kind) {
        return switch (kind) {
            case KIND_MATH -> "matematica";
            case KIND_PROG -> "programacao";
            default -> throw new IllegalArgumentException("tipo de aula inválido: " + kind);
        };
    }

    public List<Lesson> list(String titleQuery, String area) {
        return repo.search(titleQuery, area);
    }

    /** Aulas existentes com título parecido (palavras em comum) ou igual ao informado. */
    public List<Lesson> similar(String title) {
        return repo.search(null, null).stream()
                .filter(l -> l.status() != LessonStatus.FALHOU)
                .filter(l -> LessonFiles.similarity(l.title(), title) >= SIMILAR_THRESHOLD).toList();
    }

    public static String command(String kind, String content, String topics) {
        return kind + " " + content.trim() + (topics == null || topics.isBlank() ? "" : ": " + topics.trim());
    }

    /** Ao iniciar o app: aulas que ficaram GERANDO (app fechado no meio) viram FALHOU. */
    public int recoverStuck() {
        return repo.failGenerating("app fechado durante a geração");
    }

    /** A aula espera sua vez (outra está gerando). */
    public boolean isQueued(long lessonId) {
        return queued.contains(lessonId);
    }

    /**
     * Registra a aula como GERANDO e enfileira. Só uma aula gera por vez (a detecção dos arquivos novos é por data).
     * O futuro completa com a aula já atualizada (PRONTA/FALHOU), depois de mover os arquivos.
     */
    public CompletableFuture<Lesson> generate(String kind, String content, String topics, BudgetGate gate) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("conteúdo vazio");
        String area = areaOf(kind);
        Config cfg = config.get();
        Instant created = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Lesson lesson = repo.insert(new Lesson(0, kind, area, content.trim(),
                topics == null || topics.isBlank() ? null : topics.trim(), LessonStatus.GERANDO, null, null, null, created));
        Duration timeout = Duration.ofMinutes(Math.max(TIMEOUT.toMinutes(), cfg.aiTimeoutMinutes()));
        AiRequest req = new AiRequest(AiRunnerKind.CLAUDE, cfg.model(Config.MODEL_LESSONS), AiTask.AULA,
                command(kind, content, topics), List.of(), timeout);
        CompletableFuture<Lesson> out = new CompletableFuture<>();
        queued.add(lesson.id());
        worker.execute(() -> {
            queued.remove(lesson.id());
            Instant start = clock.instant().truncatedTo(ChronoUnit.SECONDS);
            Lesson done;
            try {
                TaskHandle<?> h = ai.submit(req, true, gate);
                try {
                    h.result().get();
                    done = collect(lesson, cfg, start);
                } catch (CancellationException e) {
                    done = lesson.withResult(LessonStatus.FALHOU, null, null, "Cancelada.");
                } catch (java.util.concurrent.ExecutionException e) {
                    done = lesson.withResult(LessonStatus.FALHOU, null, null, rootMessage(e));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                done = lesson.withResult(LessonStatus.FALHOU, null, null, "Interrompida.");
            } catch (RuntimeException e) {
                done = lesson.withResult(LessonStatus.FALHOU, null, null, rootMessage(e));
            }
            try {
                repo.update(done);
            } catch (RuntimeException e) {
                out.completeExceptionally(e);
                return;
            }
            out.complete(done);
        });
        return out;
    }

    private Lesson collect(Lesson lesson, Config cfg, Instant start) {
        Path root = AppPaths.expand(cfg.estudosRepo());
        Path aulas = root.resolve("aulas");
        Path dest0 = aulas.resolve(lesson.area()).resolve(LessonFiles.slug(lesson.title()));
        Path dest = LessonFiles.freeDir(dest0);
        try {
            // margem de 2 s: sistemas de arquivos podem truncar o mtime
            var found = LessonFiles.findNew(root, aulas, start.minusSeconds(2));
            if (found.pdf() == null && found.md() == null)
                return lesson.withResult(LessonStatus.FALHOU, null, null,
                        "A IA terminou, mas nenhum PDF/.md novo apareceu em " + root);
            if (found.pdf() == null || found.md() == null)
                return lesson.withResult(LessonStatus.FALHOU, null, null,
                        "A IA não gerou os dois arquivos (PDF e .md). Encontrado: pdf=" + found.pdf() + ", md=" + found.md());
            Path pdf = LessonFiles.move(found.pdf(), dest);
            Path md = LessonFiles.move(found.md(), dest);
            return lesson.withResult(LessonStatus.PRONTA, pdf == null ? null : pdf.toString(),
                    md == null ? null : md.toString(), null);
        } catch (Exception e) {
            return lesson.withResult(LessonStatus.FALHOU, null, null, "Erro ao mover arquivos: " + e);
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while ((c instanceof java.util.concurrent.CompletionException || c instanceof java.util.concurrent.ExecutionException)
                && c.getCause() != null) c = c.getCause();
        return c.getMessage() == null ? c.toString() : c.getMessage();
    }
}
