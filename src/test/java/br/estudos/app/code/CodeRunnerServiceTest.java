package br.estudos.app.code;

import br.estudos.app.code.CodeResults.*;
import br.estudos.infra.code.JdbcCodeRepository;
import br.estudos.infra.db.Database;
import br.estudos.infra.process.SystemProcessRunner;
import br.estudos.infra.study.JdbcStudyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class CodeRunnerServiceTest {
    @TempDir Path dir;
    Database db;
    CodeRunnerService svc;

    @BeforeEach
    void setup() throws Exception {
        db = new Database(dir.resolve("t.db"));
        db.migrate();
        svc = new CodeRunnerService(new SystemProcessRunner(), new JdbcCodeRepository(db), "gcc", "g++", Duration.ofSeconds(2));
    }

    Path src(String name, String code) throws Exception {
        Path p = dir.resolve(name);
        Files.writeString(p, code);
        return p;
    }

    static final String ECHO_DOUBLE = "#include <cstdio>\nint main(){int x;scanf(\"%d\",&x);printf(\"%d\\n\",x*2);}\n";

    @Test
    void acIgnoresTrailingSpaceAndBlankLines() throws Exception {
        Path p = src("a.cpp", ECHO_DOUBLE);
        svc.addCase(p, "21\n", "42  \n\n\n");
        RunReport r = svc.runAll(p);
        assertEquals(Verdict.AC, r.verdict(), r.compileOutput());
        assertEquals(1, r.passed());
    }

    @Test
    void wrongAnswerHasDiff() throws Exception {
        Path p = src("a.cpp", ECHO_DOUBLE);
        svc.addCase(p, "2\n", "5\n");
        RunReport r = svc.runAll(p);
        assertEquals(Verdict.WA, r.verdict());
        assertTrue(r.results().get(0).detail().contains("linha 1"));
    }

    @Test
    void timeLimitKillsProcess() throws Exception {
        Path p = src("t.cpp", "int main(){volatile long x=0; for(;;) x++;}\n");
        svc.addCase(p, "", "");
        assertEquals(Verdict.TLE, svc.runAll(p).verdict());
    }

    @Test
    void runtimeErrorCarriesStderr() throws Exception {
        Path p = src("r.cpp", "#include <vector>\nint main(){std::vector<int> v(3); return v[10];}\n");
        svc.addCase(p, "", "");
        RunReport r = svc.runAll(p);
        assertEquals(Verdict.RE, r.verdict());
        assertFalse(r.results().get(0).detail().isBlank());
    }

    @Test
    void compileError() throws Exception {
        Path p = src("c.cpp", "int main(){ return x; }\n");
        RunReport r = svc.runAll(p);
        assertEquals(Verdict.CE, r.verdict());
        assertTrue(r.compileOutput().contains("error"));
    }

    @Test
    void cProgramCompilesWithGcc() throws Exception {
        Path p = src("a.c", "#include <stdio.h>\nint main(void){puts(\"hi\");return 0;}\n");
        svc.addCase(p, "", "hi\n");
        assertEquals(Verdict.AC, svc.runAll(p).verdict());
    }

    @Test
    void acCreditsCpCountGoalsAndLogsRun() throws Exception {
        var study = new JdbcStudyRepository(db);
        study.saveGoal(new br.estudos.domain.Goal(0, "p", br.estudos.domain.Area.CP, br.estudos.domain.GoalKind.CONTAGEM,
                br.estudos.domain.GoalPeriod.SEMANAL, 5, 0, 0, true, java.time.Instant.now()));
        Path p = src("a.cpp", ECHO_DOUBLE);
        svc.addCase(p, "1\n", "2\n");
        svc.runAll(p);
        assertEquals(1, study.goals(true).get(0).autoCountAc());
        try (var c = db.connect(); var rs = c.createStatement().executeQuery("SELECT verdict,passed,total FROM code_run")) {
            assertTrue(rs.next());
            assertEquals("AC", rs.getString(1));
            assertEquals(1, rs.getInt(2));
        }
    }

    @Test
    void stressFindsDivergenceAndSavesCase() throws Exception {
        Path sol = src("sol.cpp", "#include <cstdio>\nint main(){int x;scanf(\"%d\",&x);printf(\"%d\\n\",x==7?0:x);}\n");
        Path brute = src("brute.cpp", "#include <cstdio>\nint main(){int x;scanf(\"%d\",&x);printf(\"%d\\n\",x);}\n");
        Path gen = src("gen.py", "import sys\nprint(int(sys.argv[1]))\n");
        StressResult r = svc.stress(sol, brute, gen, 50, () -> false);
        assertTrue(r.diverged());
        assertEquals("7\n", r.newCase().input());
        assertEquals("STRESS", svc.cases(sol).get(0).origin());
    }

    @Test
    void stressWithoutDivergence() throws Exception {
        Path sol = src("sol.cpp", ECHO_DOUBLE);
        Path gen = src("gen.py", "import sys\nprint(int(sys.argv[1]))\n");
        StressResult r = svc.stress(sol, sol, gen, 5, () -> false);
        assertFalse(r.diverged());
        assertTrue(svc.cases(sol).isEmpty());
    }
}
