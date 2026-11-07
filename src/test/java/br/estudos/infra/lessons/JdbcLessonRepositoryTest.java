package br.estudos.infra.lessons;

import br.estudos.domain.Lesson;
import br.estudos.domain.LessonStatus;
import br.estudos.infra.db.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class JdbcLessonRepositoryTest {
    @TempDir Path tmp;
    JdbcLessonRepository repo;

    @BeforeEach
    void setUp() throws Exception {
        Database db = new Database(tmp.resolve("t.db"));
        db.migrate();
        repo = new JdbcLessonRepository(db);
    }

    private Lesson add(String title, String area) {
        return repo.insert(new Lesson(0, "aula_matematica", area, title, null, LessonStatus.GERANDO,
                null, null, null, Instant.parse("2026-01-01T00:00:00Z")));
    }

    @Test
    void buscaTrataCuringasLiteralmenteEIgnoraCaixa() {
        add("100% grupos", "matematica");
        add("snake_case", "programacao");
        add("Outra aula", "matematica");
        assertEquals(1, repo.search("%", null).size());
        assertEquals(1, repo.search("_", null).size());
        assertEquals(1, repo.search("GRUPOS", null).size());
        assertEquals(2, repo.search("  ", "matematica").size());
        assertEquals(0, repo.search("x'; DROP TABLE lesson;--", null).size());
        assertEquals(3, repo.search(null, "").size());
    }

    @Test
    void updatePersisteResultadoENulos() {
        Lesson l = add("T", "matematica");
        repo.update(l.withResult(LessonStatus.FALHOU, null, null, "erro"));
        Lesson r = repo.search(null, null).get(0);
        assertEquals(LessonStatus.FALHOU, r.status());
        assertEquals("erro", r.log());
        assertNull(r.pdfPath());
        assertNull(r.topics());
    }
}
