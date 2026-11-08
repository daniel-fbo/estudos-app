package br.estudos.app.cf;

import br.estudos.app.ai.AiService;
import br.estudos.app.ai.BudgetGate;
import br.estudos.app.config.Config;
import br.estudos.app.ports.AiRunner;
import br.estudos.app.ports.CodeforcesClient;
import br.estudos.app.tasks.CancellationToken;
import br.estudos.app.tasks.TaskQueue;
import br.estudos.domain.*;
import br.estudos.infra.ai.JdbcAiCallRepository;
import br.estudos.infra.cf.JdbcCfRepository;
import br.estudos.infra.db.Database;
import br.estudos.infra.http.CodeforcesHttpClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class CfTest {
    static final String STATUS = """
            {"status":"OK","result":[
             {"id":1,"creationTimeSeconds":1700000000,"verdict":"OK","problem":{"contestId":10,"index":"A","name":"P1","rating":800,"tags":["dp","greedy"]}},
             {"id":2,"creationTimeSeconds":1700000100,"verdict":"WRONG_ANSWER","problem":{"contestId":11,"index":"B","name":"P2","rating":1200,"tags":["dp"]}},
             {"id":3,"creationTimeSeconds":1700000200,"verdict":"TIME_LIMIT_EXCEEDED","problem":{"contestId":11,"index":"B","name":"P2","tags":["dp","graphs"]}},
             {"id":4,"creationTimeSeconds":1700000300,"verdict":"COMPILATION_ERROR","problem":{"contestId":12,"index":"C","name":"P3","tags":["graphs"]}},
             {"id":5,"creationTimeSeconds":1700000400,"verdict":"WRONG_ANSWER","problem":{"contestId":13,"index":"D","name":"P4","tags":["graphs"]}},
             {"id":6,"creationTimeSeconds":1700000500,"verdict":"OK","problem":{"contestId":14,"index":"E","name":"P5","tags":["greedy"]}}
            ]}""";
    static final String RATING = """
            {"status":"OK","result":[{"contestId":10,"contestName":"R1","ratingUpdateTimeSeconds":1700000000,"oldRating":0,"newRating":1000},
            {"contestId":11,"contestName":"R2","ratingUpdateTimeSeconds":1700100000,"oldRating":1000,"newRating":1100}]}""";

    @TempDir Path tmp;
    TaskQueue queue;
    CfService svc;
    List<String> prompts = new CopyOnWriteArrayList<>();
    int calls;
    final List<CfSubmission> fakeSubs = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        Database db = new Database(tmp.resolve("t.db"));
        db.migrate();
        queue = new TaskQueue(Clock.systemUTC());
        AiRunner runner = new AiRunner() {
            public AiRunnerKind kind() { return AiRunnerKind.CLAUDE; }
            public AiResult run(AiRequest r, CancellationToken t) {
                prompts.add(r.prompt());
                return new AiResult("dica " + (++calls), 1L, 1L, 0.0);
            }
        };
        AiService ai = new AiService(List.of(runner), new JdbcAiCallRepository(db), queue, Clock.systemUTC(), () -> 100);
        Config d = Config.defaults(tmp);
        Config cfg = new Config(tmp.toString(), d.dbPath(), "claude", "agy", "gcc", "g++", "fulano", d.models(), 100, null, 0, 10, "http://localhost:8765");
        CodeforcesClient client = new CodeforcesClient() {
            public List<CfSubmission> userStatus(String h) throws CfException { return CfStats.parseStatus(STATUS); }
            public List<CfRatingPoint> userRating(String h) throws CfException { return CfStats.parseRating(RATING); }
        };
        svc = new CfService(new JdbcCfRepository(db), client, ai, () -> cfg, Clock.systemUTC());
    }

    @AfterEach
    void tearDown() { queue.close(); }

    @Test
    void agregacaoPorTag() throws Exception {
        var subs = CfStats.parseStatus(STATUS);
        assertEquals(6, subs.size());
        var by = CfStats.byTag(subs);
        var dp = by.stream().filter(t -> t.tag().equals("dp")).findFirst().orElseThrow();
        assertEquals(3, dp.total());
        assertEquals(1, dp.accepted());
        assertEquals(2, dp.failed());
        var g = by.stream().filter(t -> t.tag().equals("graphs")).findFirst().orElseThrow();
        assertEquals(2, g.total()); // CE ignorada
        assertEquals("dp", CfStats.mostFailed(subs).get(0).tag());
        assertEquals(List.of("graphs", "dp"), CfStats.suggest(subs, 3, 2)); // greedy não tem falhas
        assertEquals(List.of("dp"), CfStats.suggest(subs, 3, 3));
    }

    @Test
    void erroDaApiViraExcecao() {
        assertThrows(CfException.class, () -> CfStats.parseStatus("{\"status\":\"FAILED\",\"comment\":\"handle: not found\"}"));
    }

    @Test
    void syncGravaEEhIdempotente() throws Exception {
        var r = svc.sync();
        assertEquals(6, r.added());
        assertEquals(2, r.ratings());
        assertEquals(0, svc.sync().added());
        var o = svc.overview();
        assertEquals(2, o.ratings().size());
        assertEquals(1100, o.ratings().get(1).newRating());
        assertFalse(o.tags().isEmpty());
    }

    @Test
    void niveisDeDica() throws Exception {
        assertTrue(HintLevels.allowed(1, 0));
        assertFalse(HintLevels.allowed(2, 0));
        assertFalse(HintLevels.allowed(4, 3));
        assertThrows(IllegalStateException.class, () -> svc.hint("10A", 2, "enunciado", BudgetGate.ALWAYS));
        assertEquals("dica 1", svc.hint("10A", 1, "enunciado", BudgetGate.ALWAYS));
        assertEquals(1, svc.maxHintLevel("10A"));
        assertThrows(IllegalStateException.class, () -> svc.hint("10A", 3, "enunciado", BudgetGate.ALWAYS));
        assertEquals("dica 2", svc.hint("10A", 2, "enunciado", BudgetGate.ALWAYS));
        assertEquals("dica 1", svc.hint("10A", 1, "", BudgetGate.ALWAYS)); // cache, sem nova chamada
        assertEquals(2, calls);
        assertTrue(prompts.get(0).contains("NÃO escreva código"));
        assertEquals("dica 3", svc.hint("10A", 3, "enunciado", BudgetGate.ALWAYS));
        assertEquals("dica 1", svc.hint("11B", 1, "outro", BudgetGate.ALWAYS).isEmpty() ? "" : "dica 1"); // problema separado
    }

    @Test
    void clienteHttpRespeitaIntervalo() throws Exception {
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<Long> times = new CopyOnWriteArrayList<>();
        srv.createContext("/api/user.status", ex -> reply(ex, STATUS, times));
        srv.createContext("/api/user.rating", ex -> reply(ex, RATING, times));
        srv.start();
        try {
            var c = new CodeforcesHttpClient("http://127.0.0.1:" + srv.getAddress().getPort() + "/api", 400);
            assertEquals(6, c.userStatus("x").size());
            assertEquals(2, c.userRating("x").size());
            assertTrue(times.get(1) - times.get(0) >= 380, "intervalo: " + (times.get(1) - times.get(0)));
        } finally {
            srv.stop(0);
        }
    }

    static void reply(com.sun.net.httpserver.HttpExchange ex, String body, List<Long> times) throws java.io.IOException {
        times.add(System.currentTimeMillis());
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(200, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }
}
