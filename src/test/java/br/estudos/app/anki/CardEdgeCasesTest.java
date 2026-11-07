package br.estudos.app.anki;

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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** Casos de borda do M6 escritos na revisão. */
class CardEdgeCasesTest {
    @TempDir Path tmp;
    Path repoDir;
    TaskQueue queue;
    CardService svc;
    final List<AnkiClient.Note> sent = new ArrayList<>();
    final List<String> prompts = new CopyOnWriteArrayList<>();
    volatile boolean rejectAll;

    @BeforeEach
    void setUp() throws Exception {
        repoDir = tmp.resolve("estudos");
        Files.createDirectories(repoDir);
        Database db = new Database(tmp.resolve("t.db"));
        db.migrate();
        queue = new TaskQueue(Clock.systemUTC());
        AiRunner runner = new AiRunner() {
            public AiRunnerKind kind() { return AiRunnerKind.CLAUDE; }
            public AiResult run(AiRequest r, CancellationToken t) {
                prompts.add(r.prompt());
                return new AiResult("[{\"frente\":\"a\",\"verso\":\"b\"}]", 1L, 1L, 0.0);
            }
        };
        AiService ai = new AiService(List.of(runner), new JdbcAiCallRepository(db), queue, Clock.systemUTC(), () -> 100);
        Config d = Config.defaults(tmp);
        Config cfg = new Config(repoDir.toString(), d.dbPath(), "claude", "agy", "gcc", "g++", "", d.models(), 100, null);
        AnkiClient anki = new AnkiClient() {
            public void createDeck(String deck) {}
            public List<Long> addNotes(List<Note> notes) {
                sent.addAll(notes);
                List<Long> ids = new ArrayList<>();
                for (int i = 0; i < notes.size(); i++) ids.add(rejectAll ? null : 500L + i);
                return ids;
            }
        };
        svc = new CardService(new JdbcCardRepository(db), anki, ai, () -> cfg, Clock.systemUTC());
    }

    @AfterEach
    void tearDown() { queue.close(); }

    // ---- parser ----

    @Test
    void parserVazioENuloSemCard() {
        assertTrue(CardMarkdownParser.parse("").isEmpty());
        assertTrue(CardMarkdownParser.parse("> citação comum\n> sem marca").isEmpty());
    }

    @Test
    void parserAceitaCrlfEIndentacao() {
        var out = CardMarkdownParser.parse("  > **Card.** P\r\n  > ---\r\n  > R\r\n");
        assertEquals(1, out.size());
        assertEquals("P", out.get(0).front());
        assertEquals("R", out.get(0).back());
    }

    @Test
    void parserDescartaVersoVazioEFrenteVazia() {
        assertTrue(CardMarkdownParser.parse("> **Card.** P\n> ---\n>\n").isEmpty());
        assertTrue(CardMarkdownParser.parse("> **Card.**\n> ---\n> R\n").isEmpty());
    }

    @Test
    void parserFrenteMultilinhaEUltimoBlocoSemNewline() {
        var out = CardMarkdownParser.parse("> **Card.** linha1\n> linha2\n> ---\n> R");
        assertEquals(1, out.size());
        assertEquals("linha1\nlinha2", out.get(0).front());
        assertEquals("R", out.get(0).back());
    }

    @Test
    void parserSegundoSeparadorFazParteDoVerso() {
        var out = CardMarkdownParser.parse("> **Card.** P\n> ---\n> a\n> ---\n> b\n");
        assertEquals("a\n---\nb", out.get(0).back());
    }

    // ---- hash / dedup ----

    @Test
    void hashDeFrenteComQuebraDeLinhaEIgualAFrenteComEspaco() {
        assertEquals(CardMarkdownParser.hash("a\nb"), CardMarkdownParser.hash("A   B"));
    }

    @Test
    void addRejeitaVazioEDuplicataRetornaVazio() {
        assertThrows(IllegalArgumentException.class, () -> svc.add(" ", "x", Area.CP, CardOrigin.MANUAL, null));
        assertThrows(IllegalArgumentException.class, () -> svc.add("x", null, Area.CP, CardOrigin.MANUAL, null));
        assertTrue(svc.add("Q", "A", Area.CP, CardOrigin.MANUAL, null).isPresent());
        assertTrue(svc.add(" q ", "outra", Area.MATEMATICA, CardOrigin.IA, null).isEmpty());
    }

    @Test
    void editarParaFrenteDeOutroCardLancaIllegalArgument() {
        var a = svc.add("A", "1", Area.CP, CardOrigin.MANUAL, null).orElseThrow();
        svc.add("B", "2", Area.CP, CardOrigin.MANUAL, null);
        assertThrows(IllegalArgumentException.class, () -> svc.edit(a, "b", "x", Area.CP));
        assertEquals("A", svc.list(null, null).stream().filter(c -> c.id() == a.id()).findFirst().orElseThrow().front());
    }

    @Test
    void editarMantendoAFrenteFunciona() {
        var a = svc.add("A", "1", Area.CP, CardOrigin.MANUAL, null).orElseThrow();
        svc.edit(a, "A", "novo verso", Area.XADREZ);
        var c = svc.list(Area.XADREZ, null).get(0);
        assertEquals("novo verso", c.back());
    }

    @Test
    void excluirPermiteRecriarMesmaFrente() {
        var a = svc.add("A", "1", Area.CP, CardOrigin.MANUAL, null).orElseThrow();
        svc.delete(a.id());
        assertTrue(svc.add("A", "1", Area.CP, CardOrigin.MANUAL, null).isPresent());
    }

    // ---- importação ----

    @Test
    void importaPastaOcultaIgnoradaEPastaInexistenteFalha() throws Exception {
        Files.createDirectories(repoDir.resolve(".git"));
        Files.writeString(repoDir.resolve(".git/x.md"), "> **Card.** oculto\n> ---\n> r\n");
        Files.writeString(repoDir.resolve("ok.md"), "> **Card.** visivel\n> ---\n> r\n");
        assertEquals(1, svc.importFromRepo().added());

        Files.walk(repoDir).sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        assertThrows(IOException.class, () -> svc.importFromRepo());
    }

    @Test
    void importacaoNaoAbortaPorArquivoNaoUtf8() throws Exception {
        Files.write(repoDir.resolve("a_ruim.md"), new byte[]{(byte) 0xE9, (byte) 0xFF, 'x'});
        Files.writeString(repoDir.resolve("z_bom.md"), "> **Card.** P\n> ---\n> R\n");
        var r = svc.importFromRepo();
        assertEquals(1, r.added());
    }

    @Test
    void areaPeloCaminho() {
        assertEquals(Area.MATEMATICA, CardService.areaOf(Path.of("aulas/matematica/x.md")));
        assertEquals(Area.CP, CardService.areaOf(Path.of("programacao/x.md")));
        assertEquals(Area.CP, CardService.areaOf(Path.of("cp/x.md")));
        assertEquals(Area.XADREZ, CardService.areaOf(Path.of("xadrez/x.md")));
        assertEquals(Area.OUTRO, CardService.areaOf(Path.of("diario.md")));
    }

    // ---- geração ----

    @Test
    void parseDraftsCasosDeBorda() {
        assertTrue(CardService.parseDrafts("[]", 3).isEmpty());
        assertThrows(IllegalStateException.class, () -> CardService.parseDrafts(null, 3));
        assertThrows(IllegalStateException.class, () -> CardService.parseDrafts("[{\"frente\": ", 3));
        var d = CardService.parseDrafts("[1, \"x\", {\"front\":\" F \",\"back\":\"B\"}, {\"frente\":\"  \",\"verso\":\"v\"}, {\"frente\":5,\"verso\":\"v\"}]", 9);
        assertEquals(List.of(new CardDraft("F", "B")), d);
    }

    @Test
    void parseDraftsComColchetesNoTextoAposOJson() {
        var d = CardService.parseDrafts("[{\"frente\":\"a\",\"verso\":\"b\"}]\nVeja a nota [1].", 5);
        assertEquals(1, d.size());
    }

    @Test
    void generateValidaArgumentosEMdPath() throws Exception {
        Path md = repoDir.resolve("a.md");
        Files.writeString(md, "x");
        Lesson ok = new Lesson(1, "aula_matematica", "matematica", "A", null, LessonStatus.PRONTA, null, md.toString(), null, null);
        Lesson semMd = new Lesson(2, "aula_matematica", "matematica", "A", null, LessonStatus.PRONTA, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> svc.generate(ok, 0, s -> true));
        assertThrows(IllegalStateException.class, () -> svc.generate(semMd, 3, s -> true));
        Lesson inexistente = new Lesson(3, "aula_matematica", "matematica", "A", null, LessonStatus.PRONTA, null, repoDir.resolve("nao.md").toString(), null, null);
        assertThrows(IOException.class, () -> svc.generate(inexistente, 3, s -> true));
    }

    @Test
    void generateTruncaAulaGrandeNoPrompt() throws Exception {
        Path md = repoDir.resolve("grande.md");
        Files.writeString(md, "x".repeat(CardService.MAX_PROMPT_CHARS + 5000));
        Lesson l = new Lesson(1, "aula_matematica", "matematica", "A", null, LessonStatus.PRONTA, null, md.toString(), null, null);
        svc.generate(l, 3, s -> true).join();
        String p = prompts.get(0);
        assertTrue(p.length() < CardService.MAX_PROMPT_CHARS + 1500, "prompt deve ter a aula truncada");
        assertTrue(p.contains("no máximo 3"));
    }

    @Test
    void areaDaAula() {
        Lesson m = new Lesson(1, "k", "matematica", "A", null, LessonStatus.PRONTA, null, null, null, null);
        Lesson p = new Lesson(1, "k", "programacao", "A", null, LessonStatus.PRONTA, null, null, null, null);
        assertEquals(Area.MATEMATICA, CardService.areaOfLesson(m));
        assertEquals(Area.CP, CardService.areaOfLesson(p));
    }

    // ---- sync ----

    @Test
    void syncRecusadosContinuamPendentesEContam() throws Exception {
        svc.add("a", "b", Area.CP, CardOrigin.MANUAL, null);
        rejectAll = true;
        var r = svc.sync();
        assertEquals(1, r.sent());
        assertEquals(0, r.ok());
        assertEquals(1, r.rejected());
        assertEquals(1, svc.list(null, false).size());
    }

    @Test
    void syncSemPendentesNaoChamaAnki() throws Exception {
        assertEquals(0, svc.sync().sent());
        assertTrue(sent.isEmpty());
    }

    @Test
    void deckPorAreaTemPrefixo() {
        for (Area a : Area.values()) assertTrue(CardService.deckOf(a).startsWith("Estudos::"));
        assertEquals("Estudos::CP", CardService.deckOf(Area.CP));
    }

    @Test
    void cardEditadoAposSyncPermaneceSincronizadoMasNaoReenvia() throws Exception {
        var c = svc.add("a", "b", Area.CP, CardOrigin.MANUAL, null).orElseThrow();
        svc.sync();
        svc.edit(svc.list(null, true).get(0), "a", "verso novo", Area.CP);
        assertEquals(1, svc.list(null, true).size());
        sent.clear();
        svc.sync();
        assertTrue(sent.isEmpty());
    }

    // ---- cliente HTTP ----

    private HttpServer server(int status, String body) throws IOException {
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/", ex -> {
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        srv.start();
        return srv;
    }

    @Test
    void clienteErroDoAnkiConnectNaoEIndisponivel() throws Exception {
        HttpServer s = server(200, "{\"result\":null,\"error\":\"model not found\"}");
        try {
            var c = new AnkiConnectClient("http://127.0.0.1:" + s.getAddress().getPort());
            var e = assertThrows(AnkiException.class, () -> c.createDeck("x"));
            assertFalse(e.unavailable());
            assertTrue(e.getMessage().contains("model not found"));
        } finally { s.stop(0); }
    }

    @Test
    void clienteHttp500ELancaAnkiException() throws Exception {
        HttpServer s = server(500, "boom");
        try {
            var c = new AnkiConnectClient("http://127.0.0.1:" + s.getAddress().getPort());
            assertThrows(AnkiException.class, () -> c.createDeck("x"));
        } finally { s.stop(0); }
    }

    @Test
    void clienteAddNotesComTamanhoDeRespostaErrado() throws Exception {
        HttpServer s = server(200, "{\"result\":[1],\"error\":null}");
        try {
            var c = new AnkiConnectClient("http://127.0.0.1:" + s.getAddress().getPort());
            var notes = List.of(new AnkiClient.Note("d", "f", "v"), new AnkiClient.Note("d", "g", "w"));
            assertThrows(AnkiException.class, () -> c.addNotes(notes));
        } finally { s.stop(0); }
    }

    @Test
    void clienteRespostaNaoJsonLancaAnkiException() throws Exception {
        HttpServer s = server(200, "<html>nao e json</html>");
        try {
            var c = new AnkiConnectClient("http://127.0.0.1:" + s.getAddress().getPort());
            assertThrows(AnkiException.class, () -> c.createDeck("x"));
        } finally { s.stop(0); }
    }
}
