package br.estudos.infra.code;

import br.estudos.domain.Area;
import br.estudos.domain.Goal;
import br.estudos.domain.GoalKind;
import br.estudos.domain.GoalPeriod;
import br.estudos.domain.TestCase;
import br.estudos.infra.db.Database;
import br.estudos.infra.study.JdbcStudyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JdbcCodeRepositoryTest {

    @TempDir Path dir;
    Database db;
    JdbcCodeRepository repo;

    @BeforeEach
    void setup() throws Exception {
        db = new Database(dir.resolve("code.db"));
        db.migrate();
        repo = new JdbcCodeRepository(db);
    }

    @Test
    void saveCasesAndQueryBySourcePath() {
        TestCase c1 = repo.saveCase("/src/p1.cpp", "1\n", "2\n", "MANUAL");
        TestCase c2 = repo.saveCase("/src/p1.cpp", "3\n", "4\n", "STRESS");
        TestCase other = repo.saveCase("/src/other.cpp", "10\n", "20\n", "MANUAL");

        assertTrue(c1.id() > 0);
        assertTrue(c2.id() > c1.id());

        List<TestCase> p1Cases = repo.cases("/src/p1.cpp");
        assertEquals(2, p1Cases.size());
        assertEquals("MANUAL", p1Cases.get(0).origin());
        assertEquals("STRESS", p1Cases.get(1).origin());
        assertEquals("1\n", p1Cases.get(0).input());

        List<TestCase> otherCases = repo.cases("/src/other.cpp");
        assertEquals(1, otherCases.size());
        assertEquals(other.id(), otherCases.get(0).id());

        repo.deleteCase(c1.id());
        List<TestCase> remaining = repo.cases("/src/p1.cpp");
        assertEquals(1, remaining.size());
        assertEquals(c2.id(), remaining.get(0).id());
    }

    @Test
    void saveRunInsertsExecutionRecord() throws Exception {
        repo.saveRun("/src/prog.cpp", "WA", 2, 3, "linha 1: esperado [1], obtido [2]");

        try (var conn = db.connect();
             var ps = conn.prepareStatement("SELECT source_path, verdict, passed, total, detail, ran_at FROM code_run")) {
            var rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals("/src/prog.cpp", rs.getString("source_path"));
            assertEquals("WA", rs.getString("verdict"));
            assertEquals(2, rs.getInt("passed"));
            assertEquals(3, rs.getInt("total"));
            assertEquals("linha 1: esperado [1], obtido [2]", rs.getString("detail"));
            assertNotNull(rs.getString("ran_at"));
            assertFalse(rs.next());
        }
    }

    @Test
    void creditAcOnlyToActiveCpCountGoals() {
        var studyRepo = new JdbcStudyRepository(db);
        Instant now = Instant.now();

        Goal activeCpCount = studyRepo.saveGoal(new Goal(0, "CP Count Active", Area.CP,
                GoalKind.CONTAGEM, GoalPeriod.SEMANAL, 10, 0, 0, true, now));
        Goal inactiveCpCount = studyRepo.saveGoal(new Goal(0, "CP Count Inactive", Area.CP,
                GoalKind.CONTAGEM, GoalPeriod.SEMANAL, 10, 0, 0, false, now));
        Goal activeCpHours = studyRepo.saveGoal(new Goal(0, "CP Hours Active", Area.CP,
                GoalKind.HORAS, GoalPeriod.SEMANAL, 10, 0, 0, true, now));
        Goal activeMathCount = studyRepo.saveGoal(new Goal(0, "Math Count Active", Area.MATEMATICA,
                GoalKind.CONTAGEM, GoalPeriod.SEMANAL, 10, 0, 0, true, now));

        repo.creditAcToCpGoals();

        List<Goal> all = studyRepo.goals(false);
        assertEquals(1, findGoal(all, activeCpCount.id()).autoCountAc());
        assertEquals(0, findGoal(all, inactiveCpCount.id()).autoCountAc());
        assertEquals(0, findGoal(all, activeCpHours.id()).autoCountAc());
        assertEquals(0, findGoal(all, activeMathCount.id()).autoCountAc());

        repo.creditAcToCpGoals();
        all = studyRepo.goals(false);
        assertEquals(2, findGoal(all, activeCpCount.id()).autoCountAc());
        assertEquals(0, findGoal(all, inactiveCpCount.id()).autoCountAc());
    }

    private static Goal findGoal(List<Goal> goals, long id) {
        return goals.stream().filter(g -> g.id() == id).findFirst().orElseThrow();
    }
}
