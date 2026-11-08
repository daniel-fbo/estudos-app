package br.estudos.app;

import br.estudos.app.ai.*;
import br.estudos.app.anki.CardService;
import br.estudos.app.config.Config;
import br.estudos.app.lessons.LessonService;
import br.estudos.app.ports.AiRunner;
import br.estudos.app.tasks.CancellationToken;
import br.estudos.app.tasks.TaskQueue;
import br.estudos.domain.*;
import br.estudos.infra.ai.JdbcAiCallRepository;
import br.estudos.infra.anki.JdbcCardRepository;
import br.estudos.infra.db.Database;
import br.estudos.infra.http.AnkiConnectClient;
import br.estudos.infra.lessons.JdbcLessonRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class P2Test {
    @TempDir Path tmp;
    Database db;
    TaskQueue queue;

    @BeforeEach
    void setUp() throws Exception {
        db = new Database(tmp.resolve("t.db"));
        db.migrate();
        queue = new TaskQueue(Clock.systemUTC());
    }

    @AfterEach
    void tearDown() { queue.close(); }

    // ---- orçamento ----

    @Test
    void statusDizQualLimiteEstourou() {
        assertFalse(new BudgetStatus(1, 10, 5, 100).exceeded());
        var calls = new BudgetStatus(5, 10, 5, 100);
        assertTrue(calls.exceeded());
        assertTrue(calls.reason().contains("chamadas") && !calls.reason().contains("tokens"));
        var tokens = new BudgetStatus(1, 100, 5, 100);
        assertTrue(tokens.exceeded());
        assertTrue(tokens.reason().contains("tokens") && !tokens.reason().contains("chamadas"));
        assertTrue(new BudgetStatus(5, 100, 5, 100).reason().contains(" e "));
        assertFalse(new BudgetStatus(1, 9_999_999, 5, 0).exceeded()); // 0 = sem limite
    }

    @Test
    void orcamentoDeTokensPedeConfirmacao() throws Exception {
        AiRunner runner = new AiRunner() {
            public AiRunnerKind kind() { return AiRunnerKind.CLAUDE; }
            public AiResult run(AiRequest r, CancellationToken t) { return new AiResult("ok", 60L, 50L, null); }
        };
        AiService ai = new AiService(List.of(runner), new JdbcAiCallRepository(db), queue, Clock.systemUTC(),
                () -> 100, () -> 100L, () -> 10);
        var gateSeen = new AtomicReference<BudgetStatus>();
        BudgetGate gate = s -> { gateSeen.set(s); return false; };
        ai.execute(AiRequest.of(AiRunnerKind.CLAUDE, null, AiTask.OUTRO, "a"), false, gate, CancellationToken.none());
        assertNull(gateSeen.get()); // 110 tokens gastos agora; ainda não tinha estourado
        var ex = assertThrows(BudgetDeclinedException.class, () ->
                ai.execute(AiRequest.of(AiRunnerKind.CLAUDE, null, AiTask.OUTRO, "b"), false, gate, CancellationToken.none()));
        assertTrue(gateSeen.get().tokensExceeded());
        assertFalse(gateSeen.get().callsExceeded());
        assertTrue(ex.getMessage().contains("tokens"));
    }

    @Test
    void timeoutDoConfigValeParaPedidoPadraoEAulaFicaComOMaior() throws Exception {
        List<Duration> seen = new ArrayList<>();
        AiRunner runner = new AiRunner() {
            public AiRunnerKind kind() { return AiRunnerKind.CLAUDE; }
            public AiResult run(AiRequest r, CancellationToken t) { seen.add(r.timeout()); return new AiResult("ok", 1L, 1L, null); }
        };
        AiService ai = new AiService(List.of(runner), new JdbcAiCallRepository(db), queue, Clock.systemUTC(),
                () -> 100, () -> 0L, () -> 25);
        ai.execute(AiRequest.of(AiRunnerKind.CLAUDE, null, AiTask.OUTRO, "a"), true, BudgetGate.ALWAYS, CancellationToken.none());
        ai.execute(AiRequest.of(AiRunnerKind.CLAUDE, null, AiTask.OUTRO, "b").withTimeout(Duration.ofMinutes(60)),
                true, BudgetGate.ALWAYS, CancellationToken.none());
        ai.execute(AiRequest.of(AiRunnerKind.CLAUDE, null, AiTask.OUTRO, "c").withTimeout(Duration.ofMinutes(5)),
                true, BudgetGate.ALWAYS, CancellationToken.none());
        assertEquals(List.of(Duration.ofMinutes(25), Duration.ofMinutes(60), Duration.ofMinutes(25)), seen);
    }

    // ---- aulas ----

    @Test
    void aulasGerandoViramFalhouAoIniciar() {
        var repo = new JdbcLessonRepository(db);
        var svc = new LessonService(null, repo, () -> Config.defaults(tmp), Clock.systemUTC());
        repo.insert(new Lesson(0, "aula_matematica", "matematica", "A", null, LessonStatus.GERANDO, null, null, null,
                java.time.Instant.parse("2026-01-01T00:00:00Z")));
        repo.insert(new Lesson(0, "aula_matematica", "matematica", "B", null, LessonStatus.PRONTA, "p", "m", null,
                java.time.Instant.parse("2026-01-02T00:00:00Z")));
        assertEquals(1, svc.recoverStuck());
        var all = repo.search(null, null);
        assertEquals(LessonStatus.PRONTA, all.stream().filter(l -> l.title().equals("B")).findFirst().get().status());
        var a = all.stream().filter(l -> l.title().equals("A")).findFirst().get();
        assertEquals(LessonStatus.FALHOU, a.status());
        assertEquals("app fechado durante a geração", a.log());
        assertEquals(0, svc.recoverStuck());
    }

    @Test
    void duasAulasGeramUmaDeCadaVez() throws Exception {
        Path repoDir = tmp.resolve("estudos");
        Files.createDirectories(repoDir);
        CountDownLatch firstIn = new CountDownLatch(1), release = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger running = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger maxRunning = new java.util.concurrent.atomic.AtomicInteger();
        List<String> order = new CopyOnWriteArrayList<>();
        AiRunner runner = new AiRunner() {
            public AiRunnerKind kind() { return AiRunnerKind.CLAUDE; }
            public AiResult run(AiRequest r, CancellationToken t) throws AiException {
                maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
                order.add(r.prompt());
                firstIn.countDown();
                try {
                    if (order.size() == 1) assertTrue(release.await(10, TimeUnit.SECONDS));
                    String n = r.prompt().contains("Alfa") ? "alfa" : "beta";
                    Files.writeString(repoDir.resolve(n + ".pdf"), "%PDF");
                    Files.writeString(repoDir.resolve(n + ".md"), "# " + n);
                } catch (Exception e) { throw new AiException(e.toString()); }
                finally { running.decrementAndGet(); }
                return new AiResult("ok", 1L, 1L, null);
            }
        };
        Config c = new Config(repoDir.toString(), "x", "claude", "agy", "gcc", "g++", "", Config.defaults(tmp).models(),
                100, null, 0, 10, "http://localhost:8765");
        AiService ai = new AiService(List.of(runner), new JdbcAiCallRepository(db), queue, Clock.systemUTC(), () -> 100);
        var svc = new LessonService(ai, new JdbcLessonRepository(db), () -> c, Clock.systemUTC());

        var f1 = svc.generate(LessonService.KIND_MATH, "Alfa", null, BudgetGate.ALWAYS);
        assertTrue(firstIn.await(10, TimeUnit.SECONDS));
        var f2 = svc.generate(LessonService.KIND_MATH, "Beta", null, BudgetGate.ALWAYS);
        Thread.sleep(300);
        assertEquals(1, order.size()); // a segunda espera
        var queuedLesson = svc.list("Beta", null).get(0);
        assertTrue(svc.isQueued(queuedLesson.id()));
        assertFalse(svc.isQueued(svc.list("Alfa", null).get(0).id()));
        release.countDown();
        Lesson l1 = f1.get(15, TimeUnit.SECONDS), l2 = f2.get(15, TimeUnit.SECONDS);
        assertEquals(1, maxRunning.get());
        assertEquals(LessonStatus.PRONTA, l1.status());
        assertEquals(LessonStatus.PRONTA, l2.status());
        assertTrue(l1.pdfPath().contains("alfa.pdf"));
        assertTrue(l2.pdfPath().contains("beta.pdf")); // cada aula pegou só os seus arquivos
    }

    // ---- Anki ----

    record Req(String body) {}

    HttpServer anki(List<String> bodies, boolean[] error) throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.createContext("/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes());
            bodies.add(body);
            String resp = body.contains("updateNoteFields") ? "{\"result\":null,\"error\":" + (error[0] ? "\"boom\"" : "null") + "}"
                    : body.contains("addNotes") ? "{\"result\":[77],\"error\":null}" : "{\"result\":1,\"error\":null}";
            byte[] b = resp.getBytes();
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        s.start();
        return s;
    }

    CardService cards(String url) {
        Config c = Config.defaults(tmp);
        AiService ai = new AiService(List.of(), new JdbcAiCallRepository(db), queue, Clock.systemUTC(), () -> 100);
        return new CardService(new JdbcCardRepository(db), new AnkiConnectClient(() -> url), ai, () -> c, Clock.systemUTC());
    }

    @Test
    void editarCardSincronizadoAtualizaANotaComAnkiAberto() throws Exception {
        List<String> bodies = new CopyOnWriteArrayList<>();
        HttpServer s = anki(bodies, new boolean[]{false});
        try {
            var svc = cards("http://127.0.0.1:" + s.getAddress().getPort());
            svc.add("f", "v", Area.CP, CardOrigin.MANUAL, null);
            svc.sync();
            bodies.clear();
            assertFalse(svc.edit(svc.list(null, true).get(0), "f", "verso novo", Area.CP));
            assertEquals(1, bodies.size());
            assertTrue(bodies.get(0).contains("updateNoteFields") && bodies.get(0).contains("\"id\":77")
                    && bodies.get(0).contains("verso novo"));
            assertEquals(1, svc.list(null, true).size());
            assertEquals(0, svc.list(null, false).size());
        } finally { s.stop(0); }
    }

    @Test
    void editarComAnkiFechadoFicaPendenteESyncAtualizaANotaSemCriarOutra() throws Exception {
        List<String> bodies = new CopyOnWriteArrayList<>();
        HttpServer s = anki(bodies, new boolean[]{false});
        int port = s.getAddress().getPort();
        String[] url = {"http://127.0.0.1:" + port};
        Config c = Config.defaults(tmp);
        AiService ai = new AiService(List.of(), new JdbcAiCallRepository(db), queue, Clock.systemUTC(), () -> 100);
        var svc = new CardService(new JdbcCardRepository(db), new AnkiConnectClient(() -> url[0]), ai, () -> c, Clock.systemUTC());
        svc.add("f", "v", Area.CP, CardOrigin.MANUAL, null);
        svc.sync();
        s.stop(0); // Anki "fecha"
        assertTrue(svc.edit(svc.list(null, true).get(0), "f", "offline", Area.CP));
        var pending = svc.list(null, false);
        assertEquals(1, pending.size());
        assertNull(pending.get(0).syncedAt());
        assertEquals("offline", pending.get(0).back());
        assertEquals(77L, pending.get(0).ankiNoteId());
        assertFalse(pending.get(0).synced());

        HttpServer s2 = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s2.stop(0);
        List<String> bodies2 = new CopyOnWriteArrayList<>();
        HttpServer s3 = anki(bodies2, new boolean[]{false});
        try {
            url[0] = "http://127.0.0.1:" + s3.getAddress().getPort();
            var r = svc.sync();
            assertEquals(1, r.ok());
            assertTrue(bodies2.stream().anyMatch(b -> b.contains("updateNoteFields") && b.contains("offline")));
            assertTrue(bodies2.stream().noneMatch(b -> b.contains("addNotes")));
            assertEquals(0, svc.list(null, false).size());
        } finally { s3.stop(0); }
    }

    @Test
    void ankiUrlEhLidaACadaChamada() {
        var c = Config.defaults(tmp);
        assertEquals("http://localhost:8765", c.ankiUrl());
        assertEquals(10, c.aiTimeoutMinutes());
        assertEquals(0, c.dailyTokenBudget());
    }
}
