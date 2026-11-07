package br.estudos.infra.library;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Casos de borda do índice Lucene (revisão M5). */
class LucenePageIndexEdgeTest {
    @TempDir Path tmp;
    LucenePageIndex idx;

    @BeforeEach
    void up() throws Exception { idx = new LucenePageIndex(tmp.resolve("l")); }

    @AfterEach
    void down() throws Exception { idx.close(); }

    @Test
    void consultaVaziaOuEmBrancoNaoQuebra() throws Exception {
        idx.replaceBook(1, List.of("alguma coisa"));
        assertTrue(idx.search("", 10).isEmpty());
        assertTrue(idx.search("   ", 10).isEmpty());
        assertTrue(idx.search(null, 10).isEmpty());
    }

    @Test
    void caracteresEspeciaisDeQueryNaoQuebram() throws Exception {
        idx.replaceBook(1, List.of("f(x) = a+b [teste] \"aspas\" c:d"));
        assertDoesNotThrow(() -> idx.search("f(x) +a -b [ \" c:d ~ * ?", 10));
        assertFalse(idx.search("aspas", 10).isEmpty());
    }

    @Test
    void operadoresBooleanosSoltosNaoDevemZerarABusca() throws Exception {
        idx.replaceBook(1, List.of("Dijkstra em grafos"));
        // "AND"/"OR"/"NOT" não são escapados por QueryParser.escape: "Dijkstra AND" vira erro de sintaxe
        assertFalse(idx.search("Dijkstra AND", 10).isEmpty(), "consulta terminada em AND retornou vazio");
        assertFalse(idx.search("NOT Dijkstra", 10).isEmpty(), "NOT inicial retornou vazio");
    }

    @Test
    void buscaIgnoraAcentos() throws Exception {
        idx.replaceBook(1, List.of("Caminhos mínimos em grafos e função"));
        assertFalse(idx.search("minimos", 10).isEmpty(), "sem acento não achou 'mínimos'");
        assertFalse(idx.search("funcao", 10).isEmpty(), "sem acento não achou 'função'");
    }

    @Test
    void snippetComTextoQueMudaDeTamanhoAoMinusculizar() throws Exception {
        // 'İ' (U+0130) vira 2 chars em toLowerCase: índices de low deixam de bater com os de flat
        String texto = "İ".repeat(100) + " alvo final";
        idx.replaceBook(1, List.of(texto));
        assertDoesNotThrow(() -> idx.search("alvo", 10));
    }

    @Test
    void paginaVaziaOuNulaEhIndexadaSemErro() throws Exception {
        idx.replaceBook(1, java.util.Arrays.asList("", null, "texto da três"));
        var hits = idx.search("três", 10);
        assertEquals(1, hits.size());
        assertEquals(3, hits.get(0).page());
        assertNull(idx.pageText(1, 99));
    }

    @Test
    void reindexarSubstituiEIsolaLivros() throws Exception {
        idx.replaceBook(1, List.of("alfa", "beta"));
        idx.replaceBook(2, List.of("alfa gama"));
        idx.replaceBook(1, List.of("delta"));
        assertTrue(idx.search("beta", 10).isEmpty());
        assertEquals(1, idx.topPages(2, "alfa", 8).size());
        assertEquals(2, idx.topPages(2, "alfa", 8).get(0).bookId());
        assertEquals("delta", idx.pageText(1, 1).trim());
        idx.deleteBook(2);
        assertTrue(idx.search("gama", 10).isEmpty());
    }

    @Test
    void limiteDeResultadosRespeitado() throws Exception {
        idx.replaceBook(1, List.of("x termo", "y termo", "z termo", "w termo"));
        assertEquals(2, idx.search("termo", 2).size());
        assertEquals(3, idx.topPages(1, "termo", 3).size());
    }

    @Test
    void persisteEntreAberturas() throws Exception {
        idx.replaceBook(1, List.of("persistente"));
        idx.close();
        idx = new LucenePageIndex(tmp.resolve("l"));
        assertEquals(1, idx.search("persistente", 5).size());
    }
}
