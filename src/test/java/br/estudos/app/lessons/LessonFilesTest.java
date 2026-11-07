package br.estudos.app.lessons;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class LessonFilesTest {
    @TempDir Path tmp;

    private Path file(String rel, Instant mtime) throws Exception {
        Path p = tmp.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, "x");
        Files.setLastModifiedTime(p, FileTime.from(mtime));
        return p;
    }

    @Test
    void slugEdgeCases() {
        assertEquals("aula", LessonFiles.slug("!!!"));
        assertEquals("aula", LessonFiles.slug(""));
        assertEquals("arvores-binarias", LessonFiles.slug("  Árvores   Binárias!! "));
        String longSlug = LessonFiles.slug("a".repeat(100) + " b");
        assertTrue(longSlug.length() <= 60);
        assertFalse(longSlug.endsWith("-"));
    }

    @Test
    void similarityEdgeCases() {
        assertEquals(0, LessonFiles.similarity("", "grupos"));
        assertEquals(0, LessonFiles.similarity("a b", "a b")); // só palavras curtas
        assertEquals(1.0, LessonFiles.similarity("Teoria dos grupos", "grupos teoria dos"));
        assertEquals(0, LessonFiles.similarity("grafos", "árvores"));
    }

    @Test
    void findNewIgnoraAntigosOcultosEAulas() throws Exception {
        Instant start = Instant.now();
        file("velho.pdf", start.minusSeconds(3600));
        file(".git/x.pdf", start.plusSeconds(5));
        file("node_modules/y.md", start.plusSeconds(5));
        file("aulas/matematica/a/a.pdf", start.plusSeconds(5));
        var f = LessonFiles.findNew(tmp, tmp.resolve("aulas"), start);
        assertNull(f.pdf());
        assertNull(f.md());
    }

    @Test
    void findNewCasaMdComPdfPeloNome() throws Exception {
        Instant start = Instant.now();
        Path pdf = file("o/grupos.pdf", start.plusSeconds(10));
        Path md = file("o/grupos.md", start.plusSeconds(1));
        file("o/outro.md", start.plusSeconds(20));
        var f = LessonFiles.findNew(tmp, null, start);
        assertEquals(pdf, f.pdf());
        assertEquals(md, f.md());
    }

    @Test
    void findNewDiretorioInexistente() {
        var f = LessonFiles.findNew(tmp.resolve("nao-existe"), null, Instant.now());
        assertNull(f.pdf());
        assertNull(f.md());
    }

    @Test
    void moveNuloEMesmoDestino() throws Exception {
        assertNull(LessonFiles.move(null, tmp));
        Path p = file("d/a.pdf", Instant.now());
        assertEquals(p, LessonFiles.move(p, p.getParent()));
        assertTrue(Files.exists(p));
    }

    @Test
    void naoMoveMdSemRelacaoComOPdf() throws Exception {
        Instant start = Instant.now();
        file("o/grupos.pdf", start.plusSeconds(10));
        file("o/grupos.md", start.plusSeconds(10));
        Path nota = file("notas/minha-nota.md", start.plusSeconds(20));
        var f = LessonFiles.findNew(tmp, null, start);
        assertNotEquals(nota, f.md());
    }
}
