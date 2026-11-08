package br.estudos.app.anki;

import br.estudos.app.ai.AiException;
import br.estudos.app.ai.AiService;
import br.estudos.app.config.Config;
import br.estudos.app.ports.AiRunner;
import br.estudos.app.ports.AnkiClient;
import br.estudos.app.tasks.CancellationToken;
import br.estudos.app.tasks.TaskQueue;
import br.estudos.domain.*;
import br.estudos.infra.ai.JdbcAiCallRepository;
import br.estudos.infra.anki.JdbcCardRepository;
import br.estudos.infra.db.Database;
import br.estudos.infra.http.AnkiConnectClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class CardServiceTest {
    @TempDir Path tmp;
    Path repoDir;
    TaskQueue queue;
    CardService svc;
    FakeAnki anki = new FakeAnki();
    volatile String aiAnswer = "[]";

    static class FakeAnki implements AnkiClient {
        List<String> decks = new ArrayList<>();
        List<Note> notes = new ArrayList<>();
        boolean down;
        public void createDeck(String d) throws AnkiException { check(); decks.add(d); }
        public List<Long> addNotes(List<Note> n) throws AnkiException {
            check();
            List<Long> ids = new ArrayList<>();
            for (Note x : n) { notes.add(x); ids.add(1000L + notes.size()); }
            return ids;
        }
        public void updateNoteFields(long id, String f, String b) throws AnkiException { check(); }
        void check() throws AnkiException { if (down) throw new AnkiException("Anki fechado", true, null); }
    }

    @BeforeEach
    void setUp() throws Exception {
        repoDir = tmp.resolve("estudos");
        Files.createDirectories(repoDir);
        Database db = new Database(tmp.resolve("t.db"));
        db.migrate();
        queue = new TaskQueue(Clock.systemUTC());
        AiRunner runner = new AiRunner() {
            public AiRunnerKind kind() { return AiRunnerKind.CLAUDE; }
            public AiResult run(AiRequest r, CancellationToken t) { return new AiResult(aiAnswer, 1L, 1L, 0.0); }
        };
        AiService ai = new AiService(List.of(runner), new JdbcAiCallRepository(db), queue, Clock.systemUTC(), () -> 100);
        Config d = Config.defaults(tmp);
        Config cfg = new Config(repoDir.toString(), d.dbPath(), "claude", "agy", "gcc", "g++", "", d.models(), 100, null, 0, 10, "http://localhost:8765");
        svc = new CardService(new JdbcCardRepository(db), anki, ai, () -> cfg, Clock.systemUTC());
    }

    @AfterEach
    void tearDown() { queue.close(); }

    @Test
    void parserExtraiBlocos() {
        String md = """
                # Aula
                texto
                > **Card.** O que é um grupo?
                > ---
                > Conjunto com operação associativa,
                > neutro e inversos.

                > **Card.** Frente 2
                > ---
                > Verso 2
                > **Card.** Sem verso
                """;
        var out = CardMarkdownParser.parse(md);
        assertEquals(2, out.size());
        assertEquals("O que é um grupo?", out.get(0).front());
        assertEquals("Conjunto com operação associativa,\nneutro e inversos.", out.get(0).back());
        assertEquals("Verso 2", out.get(1).back());
    }

    @Test
    void hashIgnoraCaixaEEspacos() {
        assertEquals(CardMarkdownParser.hash("Olá  mundo"), CardMarkdownParser.hash(" olá mundo "));
        assertNotEquals(CardMarkdownParser.hash("a"), CardMarkdownParser.hash("b"));
    }

    @Test
    void importaEDeduplica() throws Exception {
        Files.createDirectories(repoDir.resolve("aulas/matematica/x"));
        Files.writeString(repoDir.resolve("aulas/matematica/x/a.md"),
                "> **Card.** P1\n> ---\n> R1\n\n> **Card.** P2\n> ---\n> R2\n");
        Files.writeString(repoDir.resolve("b.md"), "> **Card.** p1\n> ---\n> outra\n");
        var r = svc.importFromRepo();
        assertEquals(3, r.found());
        assertEquals(2, r.added());
        var r2 = svc.importFromRepo();
        assertEquals(0, r2.added());
        assertEquals(2, svc.list(null, null).size());
    }

    @Test
    void geraDeAulaParseiaJsonCercadoPorTexto() throws Exception {
        Path md = repoDir.resolve("aula.md");
        Files.writeString(md, "# aula");
        aiAnswer = "Claro!\n```json\n[{\"frente\":\"F1\",\"verso\":\"V1\"},{\"frente\":\"F2\"},{\"frente\":\"F3\",\"verso\":\"V3\"},{\"frente\":\"F4\",\"verso\":\"V4\"}]\n```";
        Lesson l = new Lesson(1, "aula_matematica", "matematica", "Aula", null, LessonStatus.PRONTA, null, md.toString(), null, null);
        var drafts = svc.generate(l, 2, s -> true).join();
        assertEquals(List.of(new CardDraft("F1", "V1"), new CardDraft("F3", "V3")), drafts);
        assertEquals(0, svc.list(null, null).size(), "nada é salvo sem aprovação");
        assertEquals(2, svc.saveDrafts(drafts, Area.MATEMATICA, CardOrigin.AULA, "Aula"));
    }

    @Test
    void jsonInvalidoFalha() {
        assertThrows(IllegalStateException.class, () -> CardService.parseDrafts("nada", 5));
    }

    @Test
    void sincronizaUmBaralhoPorArea() throws Exception {
        svc.add("a", "b", Area.CP, CardOrigin.MANUAL, null);
        svc.add("c", "d", Area.MATEMATICA, CardOrigin.MANUAL, null);
        var r = svc.sync();
        assertEquals(2, r.ok());
        assertTrue(anki.decks.containsAll(List.of("Estudos::CP", "Estudos::Matemática")));
        assertEquals(0, svc.list(null, false).size());
        assertEquals(0, svc.sync().sent());
    }

    @Test
    void ankiFechado() {
        svc.add("a", "b", Area.CP, CardOrigin.MANUAL, null);
        anki.down = true;
        var e = assertThrows(AnkiException.class, () -> svc.sync());
        assertTrue(e.unavailable());
        assertEquals(1, svc.list(null, false).size());
    }

    @Test
    void clienteAnkiConnectComServidorFalso() throws Exception {
        List<String> bodies = new CopyOnWriteArrayList<>();
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            bodies.add(body);
            String resp = body.contains("\"addNotes\"") ? "{\"result\":[111,null],\"error\":null}"
                    : body.contains("\"createDeck\"") ? "{\"result\":5,\"error\":null}"
                    : "{\"result\":null,\"error\":\"unsupported action\"}";
            byte[] b = resp.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        srv.start();
        try {
            var c = new AnkiConnectClient("http://127.0.0.1:" + srv.getAddress().getPort());
            c.createDeck("Estudos::CP");
            var ids = c.addNotes(List.of(new AnkiClient.Note("Estudos::CP", "f", "v"), new AnkiClient.Note("Estudos::CP", "g", "w")));
            assertEquals(java.util.Arrays.asList(111L, null), ids);
            assertTrue(bodies.get(1).contains("\"deckName\":\"Estudos::CP\"") && bodies.get(1).contains("\"Front\":\"f\""));
        } finally {
            srv.stop(0);
        }
    }

    @Test
    void clienteSemServidorDizQueAnkiEstaFechado() throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = s.getAddress().getPort();
        s.stop(0);
        var e = assertThrows(AnkiException.class, () -> new AnkiConnectClient("http://127.0.0.1:" + port).createDeck("x"));
        assertTrue(e.unavailable());
        assertTrue(e.getMessage().contains("Anki"));
    }
}
