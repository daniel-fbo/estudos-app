package br.estudos.app.library;

import br.estudos.app.ai.AiService;
import br.estudos.app.config.Config;
import br.estudos.app.tasks.CancellationToken;
import br.estudos.app.tasks.TaskQueue;
import br.estudos.domain.*;
import br.estudos.infra.ai.JdbcAiCallRepository;
import br.estudos.infra.db.Database;
import br.estudos.infra.library.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Casos de borda do LibraryService (revisão M5). */
class LibraryServiceEdgeTest {
    @TempDir Path tmp;
    TaskQueue queue;
    LucenePageIndex index;
    LibraryService svc;

    @BeforeEach
    void up() throws Exception {
        Database db = new Database(tmp.resolve("t.db"));
        db.migrate();
        queue = new TaskQueue(Clock.systemUTC());
        AiService ai = new AiService(List.of(), new JdbcAiCallRepository(db), queue, Clock.systemUTC(), () -> 100);
        index = new LucenePageIndex(tmp.resolve("lucene"));
        svc = new LibraryService(new JdbcBookRepository(db), new PdfBoxReader(), index, ai,
                () -> Config.defaults(tmp), Clock.systemUTC());
        Files.createDirectories(tmp.resolve("pdfs/sub"));
    }

    @AfterEach
    void down() throws Exception { queue.close(); index.close(); }

    LibraryService.ImportResult imp() throws Exception {
        return svc.importFolder(tmp.resolve("pdfs"), Area.MATEMATICA, BookKind.LIVRO, m -> {}, CancellationToken.none());
    }

    @Test
    void pdfCorrompidoNaoImpedeOsDemais() throws Exception {
        LibraryServiceTest.pdf(tmp.resolve("pdfs/ok.pdf"), "Bom", "texto valido");
        Files.writeString(tmp.resolve("pdfs/ruim.pdf"), "isto não é um pdf");
        var r = imp();
        assertEquals(1, r.imported());
        assertEquals(1, r.failures().size());
        assertTrue(r.failures().get(0).startsWith("ruim.pdf"));
        assertEquals(1, svc.list(null, null, null).size());
    }

    @Test
    void varreSubpastasEIgnoraExtensaoMaiuscula() throws Exception {
        LibraryServiceTest.pdf(tmp.resolve("pdfs/sub/A.PDF"), null, "conteudo");
        Files.writeString(tmp.resolve("pdfs/leia.txt"), "x");
        var r = imp();
        assertEquals(1, r.imported());
        assertEquals("A", svc.list(null, null, null).get(0).title());
    }

    @Test
    void pastaSemPdfs() throws Exception {
        var r = imp();
        assertEquals(0, r.imported());
        assertTrue(r.failures().isEmpty());
    }

    @Test
    void importacaoCanceladaPropagaCancelamento() throws Exception {
        LibraryServiceTest.pdf(tmp.resolve("pdfs/a.pdf"), null, "um");
        var tok = new CancellationToken();
        tok.cancel();
        assertThrows(java.util.concurrent.CancellationException.class,
                () -> svc.importFolder(tmp.resolve("pdfs"), Area.MATEMATICA, BookKind.LIVRO, m -> {}, tok));
    }

    @Test
    void livroCadastradoSemIndiceEhIndexadoNaReimportacao() throws Exception {
        Path f = tmp.resolve("pdfs/a.pdf");
        LibraryServiceTest.pdf(f, "A", "palavra unica");
        svc.add(new Book(0, "A", null, Area.MATEMATICA, BookKind.LIVRO, f.toAbsolutePath().toString(), null, 0,
                BookStatus.QUERO_LER, null, null, null));
        var r = imp();
        assertEquals(1, r.imported());
        assertEquals(1, svc.list(null, null, null).size());
        assertEquals(1, svc.search("unica", 5).size());
    }

    @Test
    void progressoLimites() throws Exception {
        LibraryServiceTest.pdf(tmp.resolve("pdfs/a.pdf"), "A", "p1", "p2", "p3");
        imp();
        Book b = svc.list(null, null, null).get(0);
        assertEquals(0, svc.updateProgress(b, -5).currentPage());
        assertEquals(BookStatus.QUERO_LER, svc.updateProgress(b, -5).status());
        Book lido = svc.updateProgress(b, 3);
        assertEquals(BookStatus.LIDO, lido.status());
        // voltar uma página de um livro LIDO deve reabrir a leitura
        assertEquals(BookStatus.LENDO, svc.updateProgress(lido, 1).status());
    }

    @Test
    void adicionarTituloVazioRejeitado() {
        assertThrows(IllegalArgumentException.class, () -> svc.add(new Book(0, "  ", null, Area.MATEMATICA,
                BookKind.LIVRO, null, null, 0, BookStatus.QUERO_LER, null, null, null)));
    }

    @Test
    void perguntarSemPdfOuSemIndiceFalhaComMensagem() throws Exception {
        Book semPdf = svc.add(new Book(0, "X", null, Area.MATEMATICA, BookKind.LIVRO, null, null, 0,
                BookStatus.QUERO_LER, null, null, null));
        assertThrows(IllegalStateException.class, () -> svc.askAgy(semPdf, "q", s -> true));
        assertThrows(IllegalStateException.class, () -> svc.askClaude(semPdf, "q", s -> true));

        Path f = tmp.resolve("pdfs/a.pdf");
        LibraryServiceTest.pdf(f, "A", "conteudo");
        Book naoIndexado = svc.add(new Book(0, "A", null, Area.MATEMATICA, BookKind.LIVRO, f.toString(), 1, 0,
                BookStatus.QUERO_LER, null, null, null));
        assertThrows(IllegalStateException.class, () -> svc.askClaude(naoIndexado, "conteudo", s -> true));
    }
}
