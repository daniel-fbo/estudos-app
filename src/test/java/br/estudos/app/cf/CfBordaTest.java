package br.estudos.app.cf;

import br.estudos.infra.cf.JdbcCfRepository;
import br.estudos.infra.db.Database;
import br.estudos.infra.http.CodeforcesHttpClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CfBordaTest {
    @TempDir Path tmp;

    static CfSubmission sub(long id, String verdict, String... tags) {
        return new CfSubmission(id, 1, "A", "P", null, List.of(tags), verdict, "2023-01-01T00:00:00Z");
    }

    @Test
    void parseStatusIgnoraItensSemIdEAceitaCamposAusentes() throws Exception {
        var l = CfStats.parseStatus("""
                {"status":"OK","result":[
                 {"creationTimeSeconds":1,"verdict":"OK"},
                 {"id":7,"creationTimeSeconds":1700000000,"problem":{"name":"X"}}]}""");
        assertEquals(1, l.size());
        assertNull(l.get(0).verdict());
        assertNull(l.get(0).contestId());
        assertNull(l.get(0).rating());
        assertTrue(l.get(0).tags().isEmpty());
    }

    @Test
    void parseJsonInvalidoOuVazio() {
        assertThrows(CfException.class, () -> CfStats.parseStatus("não é json {"));
        assertThrows(CfException.class, () -> CfStats.parseRating("{\"status\":\"FAILED\"}"));
        assertThrows(Exception.class, () -> CfStats.parseStatus(""));
    }

    @Test
    void resultadoVazioEhListaVazia() throws Exception {
        assertTrue(CfStats.parseStatus("{\"status\":\"OK\",\"result\":[]}").isEmpty());
        assertTrue(CfStats.parseRating("{\"status\":\"OK\",\"result\":[]}").isEmpty());
        assertTrue(CfStats.byTag(List.of()).isEmpty());
        assertTrue(CfStats.suggest(List.of(), 3, 3).isEmpty());
    }

    @Test
    void veredictosNaoContamNemTagsRepetidas() {
        var subs = List.of(sub(1, null, "dp"), sub(2, "TESTING", "dp"), sub(3, "COMPILATION_ERROR", "dp"),
                sub(4, "OK", "dp"), sub(5, "WRONG_ANSWER", "dp", "dp"));
        var dp = CfStats.byTag(subs).get(0);
        assertEquals(1, CfStats.byTag(subs).size());
        assertEquals(3, dp.total(), "OK + WA; WA com tag repetida conta 2x");
        assertEquals(1, dp.accepted());
    }

    @Test
    void sugestaoRespeitaLimiteEMinimo() {
        var subs = new java.util.ArrayList<CfSubmission>();
        long id = 0;
        for (String t : List.of("a", "b", "c", "d"))
            for (int i = 0; i < 3; i++) subs.add(sub(id++, "WRONG_ANSWER", t));
        assertEquals(3, CfStats.suggest(subs, 3, 3).size());
        assertEquals(List.of("a", "b"), CfStats.suggest(subs, 2, 3));
        assertTrue(CfStats.suggest(subs, 3, 4).isEmpty());
        assertTrue(CfStats.suggest(subs, 0, 1).isEmpty());
    }

    @Test
    void nivelDeDicaForaDaFaixa() {
        assertFalse(HintLevels.allowed(0, 0));
        assertFalse(HintLevels.allowed(-1, 5));
        assertFalse(HintLevels.allowed(4, 3));
        assertTrue(HintLevels.allowed(2, 2)); // já liberado
        assertThrows(IllegalArgumentException.class, () -> HintLevels.instruction(0));
        assertThrows(IllegalArgumentException.class, () -> HintLevels.instruction(4));
    }

    @Test
    void repositorioAtualizaVeredictoSemDuplicarEPreservaNulos() throws Exception {
        Database db = new Database(tmp.resolve("t.db"));
        db.migrate();
        var repo = new JdbcCfRepository(db);
        assertEquals(1, repo.upsertSubmissions(List.of(new CfSubmission(1, null, null, null, null, null, null, "2023-01-01T00:00:00Z"))));
        assertEquals(0, repo.upsertSubmissions(List.of(sub(1, "OK", "dp"))));
        var l = repo.submissions();
        assertEquals(1, l.size());
        assertEquals("OK", l.get(0).verdict());
        assertEquals(List.of("dp"), l.get(0).tags());
        assertEquals(0, repo.upsertSubmissions(List.of()));
    }

    @Test
    void repositorioSubstituiRatingsEGuardaDicaPorNivel() throws Exception {
        Database db = new Database(tmp.resolve("t2.db"));
        db.migrate();
        var repo = new JdbcCfRepository(db);
        repo.replaceRatings(List.of(new CfRatingPoint(1, "A", "2023-01-01T00:00:00Z", 0, 1000)));
        repo.replaceRatings(List.of());
        assertTrue(repo.ratings().isEmpty());
        assertEquals(0, repo.maxHintLevel("x"));
        repo.saveHint("x", 2, "t", "2023-01-01T00:00:00Z");
        assertEquals(2, repo.maxHintLevel("x"));
        assertEquals(0, repo.maxHintLevel("y"));
        assertTrue(repo.hint("x", 1).isEmpty());
        assertThrows(IllegalStateException.class, () -> repo.saveHint("x", 4, "t", "2023-01-01T00:00:00Z"));
    }

    @Test
    void clienteHttpTrataHttp400ComJsonEHttp500() throws Exception {
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/api/user.status", ex -> send(ex, 400, "{\"status\":\"FAILED\",\"comment\":\"handle: not found\"}"));
        srv.createContext("/api/user.rating", ex -> send(ex, 503, "<html>"));
        srv.start();
        try {
            var c = new CodeforcesHttpClient("http://127.0.0.1:" + srv.getAddress().getPort() + "/api/", 0);
            var e1 = assertThrows(CfException.class, () -> c.userStatus("zz"));
            assertTrue(e1.getMessage().contains("not found"));
            var e2 = assertThrows(CfException.class, () -> c.userRating("zz"));
            assertTrue(e2.getMessage().contains("503"));
        } finally {
            srv.stop(0);
        }
    }

    @Test
    void clienteHttpServidorForaDoArViraCfException() {
        var c = new CodeforcesHttpClient("http://127.0.0.1:1/api", 0);
        assertThrows(CfException.class, () -> c.userStatus("x"));
    }

    static void send(com.sun.net.httpserver.HttpExchange ex, int code, String body) throws java.io.IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }
}
