package br.estudos.app.library;

import br.estudos.app.ai.AiException;
import br.estudos.app.ai.AiService;
import br.estudos.app.config.Config;
import br.estudos.app.ports.AiRunner;
import br.estudos.app.tasks.CancellationToken;
import br.estudos.app.tasks.TaskQueue;
import br.estudos.domain.*;
import br.estudos.infra.ai.JdbcAiCallRepository;
import br.estudos.infra.db.Database;
import br.estudos.infra.library.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LibraryServiceTest {
    @TempDir Path tmp;
    TaskQueue queue;
    LucenePageIndex index;
    LibraryService svc;
    final List<AiRequest> requests = new ArrayList<>();
    boolean agyFails;

    class Fake implements AiRunner {
        final AiRunnerKind k;
        Fake(AiRunnerKind k) { this.k = k; }
        public AiRunnerKind kind() { return k; }
        public AiResult run(AiRequest r, CancellationToken t) throws AiException {
            requests.add(r);
            if (k == AiRunnerKind.AGY && agyFails) throw new AiException("sem cota");
            return new AiResult("resp " + k, 1L, 1L, null);
        }
    }

    static void pdf(Path f, String title, String... pages) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            if (title != null) {
                PDDocumentInformation i = new PDDocumentInformation();
                i.setTitle(title);
                i.setAuthor("Autora");
                doc.setDocumentInformation(i);
            }
            for (String text : pages) {
                PDPage p = new PDPage();
                doc.addPage(p);
                try (PDPageContentStream cs = new PDPageContentStream(doc, p)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    cs.showText(text);
                    cs.endText();
                }
            }
            doc.save(f.toFile());
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        Database db = new Database(tmp.resolve("t.db"));
        db.migrate();
        queue = new TaskQueue(Clock.systemUTC());
        AiService ai = new AiService(List.of(new Fake(AiRunnerKind.CLAUDE), new Fake(AiRunnerKind.AGY)),
                new JdbcAiCallRepository(db), queue, Clock.systemUTC(), () -> 100);
        index = new LucenePageIndex(tmp.resolve("lucene"));
        svc = new LibraryService(new JdbcBookRepository(db), new PdfBoxReader(), index, ai,
                () -> Config.defaults(tmp), Clock.systemUTC());
        Path dir = Files.createDirectories(tmp.resolve("pdfs"));
        pdf(dir.resolve("grupos.pdf"), "Teoria dos Grupos", "Todo subgrupo tem ordem que divide a do grupo.",
                "O teorema de Lagrange relaciona ordens.");
        pdf(dir.resolve("grafos.pdf"), null, "Dijkstra encontra caminhos minimos em grafos.");
    }

    @AfterEach
    void tearDown() throws Exception {
        queue.close();
        index.close();
    }

    LibraryService.ImportResult imp() throws Exception {
        return svc.importFolder(tmp.resolve("pdfs"), Area.MATEMATICA, BookKind.LIVRO, m -> {}, CancellationToken.none());
    }

    @Test
    void importaIndexaEBusca() throws Exception {
        var r = imp();
        assertEquals(2, r.imported());
        assertTrue(r.failures().isEmpty());
        List<Book> all = svc.list(null, null, null);
        assertEquals(List.of("grafos", "Teoria dos Grupos"), all.stream().map(Book::title).toList());
        Book g = all.get(1);
        assertEquals(2, g.pages());
        assertEquals("Autora", g.authors());
        assertNotNull(g.indexedAt());

        var hits = svc.search("Lagrange", 10);
        assertEquals(1, hits.size());
        assertEquals("Teoria dos Grupos", hits.get(0).book().title());
        assertEquals(2, hits.get(0).page());
        assertTrue(hits.get(0).snippet().contains("«Lagrange»"), hits.get(0).snippet());

        var dj = svc.search("dijkstra", 10);
        assertEquals("grafos", dj.get(0).book().title());
        assertEquals(1, dj.get(0).page());
        assertTrue(svc.search("inexistente", 10).isEmpty());
    }

    @Test
    void reimportarNaoDuplica() throws Exception {
        imp();
        var r = imp();
        assertEquals(0, r.imported());
        assertEquals(2, r.skipped());
        assertEquals(2, svc.list(null, null, null).size());
    }

    @Test
    void progressoAtualizaStatus() throws Exception {
        imp();
        Book g = svc.list("Grupos", null, null).get(0);
        Book b = svc.updateProgress(g, 1);
        assertEquals(BookStatus.LENDO, b.status());
        b = svc.updateProgress(b, 99);
        assertEquals(2, b.currentPage());
        assertEquals(BookStatus.LIDO, b.status());
        assertEquals(2, svc.list(null, null, BookStatus.LIDO).get(0).currentPage());
    }

    @Test
    void excluirRemoveDoIndice() throws Exception {
        imp();
        Book g = svc.list("Grupos", null, null).get(0);
        svc.delete(g.id());
        assertTrue(svc.search("Lagrange", 10).isEmpty());
    }

    @Test
    void perguntaAgyEFallbackClaude() throws Exception {
        imp();
        Book g = svc.list("Grupos", null, null).get(0);
        assertEquals("resp AGY", svc.askAgy(g, "O que é Lagrange?", s -> true).result().get().response());
        assertEquals(1, requests.get(0).attachments().size());
        // mesma pergunta: vem do cache, sem nova chamada
        var again = svc.askAgy(g, "O que é Lagrange?", s -> true).result().get();
        assertTrue(again.fromCache());
        assertEquals(1, requests.size());

        var c = svc.askClaude(g, "Lagrange", s -> true).result().get();
        assertEquals("resp CLAUDE", c.response());
        AiRequest cr = requests.get(1);
        assertEquals(AiRunnerKind.CLAUDE, cr.runner());
        assertTrue(cr.prompt().contains("Página 2") && cr.prompt().contains("teorema de Lagrange"));
    }
}
