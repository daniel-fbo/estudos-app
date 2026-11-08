package br.estudos.app;

import br.estudos.app.ai.AiService;
import br.estudos.app.ai.BudgetStatus;
import br.estudos.app.anki.CardService;
import br.estudos.app.config.Config;
import br.estudos.app.ports.AnkiClient;
import br.estudos.app.tasks.TaskQueue;
import br.estudos.domain.Area;
import br.estudos.domain.CardOrigin;
import br.estudos.infra.ai.JdbcAiCallRepository;
import br.estudos.infra.anki.JdbcCardRepository;
import br.estudos.infra.db.Database;
import br.estudos.infra.http.AnkiConnectClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Casos de borda da revisão do P2. */
class P2EdgeTest {
    @TempDir Path tmp;

    @Test
    void limiteDeTokensZeroNuncaEstoura() {
        assertFalse(new BudgetStatus(0, 9_999_999, 100, 0).exceeded());
    }

    @Test
    void tokensIguaisAoLimiteEstouram() {
        var s = new BudgetStatus(1, 500, 100, 500);
        assertTrue(s.tokensExceeded());
        assertFalse(s.callsExceeded());
        assertTrue(s.reason().contains("tokens"));
        assertFalse(s.reason().contains("chamadas") && s.reason().contains(" e "));
    }

    @Test
    void ConfigNegativoDeTokensViraZero() {
        var d = Config.defaults(tmp);
        var c = new Config(d.estudosRepo(), d.dbPath(), d.claudePath(), d.agyPath(), d.gccPath(), d.gppPath(),
                d.codeforcesHandle(), d.models(), 30, "escuro", -5, -1, " ").withDefaultsFrom(d);
        assertEquals(0, c.dailyTokenBudget());
        assertEquals(10, c.aiTimeoutMinutes());
        assertEquals(Config.DEFAULT_ANKI_URL, c.ankiUrl());
    }

    @Test
    void urlDoAnkiSemEsquemaViraAnkiExceptionEnaoRuntime() {
        var client = new AnkiConnectClient(() -> "localhost:8765");
        var e = assertThrows(Exception.class, () -> client.createDeck("x"));
        assertInstanceOf(br.estudos.app.anki.AnkiException.class, e, "esperado AnkiException, veio " + e);
    }

    @Test
    void editarComFrenteDuplicadaNaoAlteraANotaNoAnki() throws Exception {
        var db = new Database(tmp.resolve("t.db"));
        db.migrate();
        var queue = new TaskQueue(Clock.systemUTC());
        try {
            List<String> calls = new ArrayList<>();
            AnkiClient fake = new AnkiClient() {
                
                public void createDeck(String d) { }
                public void updateNoteFields(long id, String f, String b) { calls.add(f + "|" + b); }
                public List<Long> addNotes(List<Note> n) { return n.stream().map(x -> 5L).toList(); }
            };
            Config c = Config.defaults(tmp);
            AiService ai = new AiService(List.of(), new JdbcAiCallRepository(db), queue, Clock.systemUTC(), () -> 100);
            var svc = new CardService(new JdbcCardRepository(db), fake, ai, () -> c, Clock.systemUTC());
            svc.add("a", "1", Area.CP, CardOrigin.MANUAL, null);
            svc.add("b", "2", Area.CP, CardOrigin.MANUAL, null);
            svc.sync();
            var b = svc.list(null, true).stream().filter(x -> x.front().equals("b")).findFirst().orElseThrow();
            calls.clear();
            assertThrows(IllegalArgumentException.class, () -> svc.edit(b, "a", "novo", Area.CP));
            assertTrue(calls.isEmpty(), "Anki foi alterado, mas o banco local recusou a edição: " + calls);
        } finally { queue.close(); }
    }
}
