package br.estudos.app.code;

import br.estudos.app.code.CodeResults.*;
import br.estudos.infra.code.JdbcCodeRepository;
import br.estudos.infra.db.Database;
import br.estudos.infra.process.SystemProcessRunner;
import br.estudos.app.study.StudyService;
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

    /** Meta CONTAGEM/CP que conta ACs; devolve o serviço para ler o progresso. */
    StudyService cpAcGoal() {
        var study = new StudyService(new JdbcStudyRepository(db), java.time.Clock.systemDefaultZone());
        study.createGoal("CP", br.estudos.domain.Area.CP, br.estudos.domain.GoalKind.CONTAGEM,
                br.estudos.domain.GoalPeriod.SEMANAL, 5, br.estudos.domain.GoalSource.RUNNER);
        return study;
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
        var study = cpAcGoal();
        Path p = src("a.cpp", ECHO_DOUBLE);
        svc.addCase(p, "1\n", "2\n");
        svc.runAll(p);
        svc.runAll(p); // o mesmo arquivo com AC de novo não soma outra vez
        assertEquals(1.0, study.allGoalsProgress().get(0).current());
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

    @Test
    void stressWithCppGenerator() throws Exception {
        Path sol = src("sol2.cpp", "#include <cstdio>\nint main(){int x;scanf(\"%d\",&x);printf(\"%d\\n\",x==3?0:x);}\n");
        Path brute = src("brute2.cpp", "#include <cstdio>\nint main(){int x;scanf(\"%d\",&x);printf(\"%d\\n\",x);}\n");
        Path gen = src("gen.cpp", "#include <cstdio>\n#include <cstdlib>\nint main(int, char** argv){printf(\"%d\\n\",atoi(argv[1]));}\n");
        StressResult r = svc.stress(sol, brute, gen, 10, () -> false);
        assertTrue(r.diverged());
        assertEquals("3\n", r.newCase().input());
        assertEquals("STRESS", svc.cases(sol).get(0).origin());
    }

    @Test
    void stressCanBeCancelled() throws Exception {
        Path sol = src("sol3.cpp", ECHO_DOUBLE);
        Path gen = src("gen3.py", "import sys\nprint(int(sys.argv[1]))\n");
        var count = new java.util.concurrent.atomic.AtomicInteger();
        StressResult r = svc.stress(sol, sol, gen, 20, () -> count.incrementAndGet() >= 3);
        assertFalse(r.diverged());
        assertTrue(r.message().contains("Cancelado após"));
        assertTrue(r.iterations() < 20);
    }

    @Test
    void stressGeneratorCompileError() throws Exception {
        Path sol = src("sol4.cpp", ECHO_DOUBLE);
        Path brute = src("brute4.cpp", ECHO_DOUBLE);
        Path gen = src("gen_bad.cpp", "int main(){ return invalid_syntax; }\n");
        StressResult r = svc.stress(sol, brute, gen, 5, () -> false);
        assertFalse(r.diverged());
        assertTrue(r.message().contains("Erro ao compilar o gerador"));
    }

    @Test
    void stressBruteCompileError() throws Exception {
        Path sol = src("sol5.cpp", ECHO_DOUBLE);
        Path brute = src("brute_bad.cpp", "int main(){ return invalid_syntax; }\n");
        Path gen = src("gen5.py", "import sys\nprint(int(sys.argv[1]))\n");
        StressResult r = svc.stress(sol, brute, gen, 5, () -> false);
        assertFalse(r.diverged());
        assertTrue(r.message().contains("Erro ao compilar a força bruta"));
    }

    @Test
    void multipleCasesMixedVerdictsReportsFirstFailureAndDoesNotCreditGoals() throws Exception {
        var study = cpAcGoal();
        Path p = src("mixed.cpp", ECHO_DOUBLE);
        svc.addCase(p, "1\n", "2\n");
        svc.addCase(p, "2\n", "99\n"); // WA
        RunReport r = svc.runAll(p);
        assertEquals(Verdict.WA, r.verdict());
        assertEquals(1, r.passed());
        assertEquals(2, r.total());
        assertEquals(2, r.results().size());
        assertEquals(0.0, study.allGoalsProgress().get(0).current());
    }

    @Test
    void emptyCasesDoesNotCreditGoals() throws Exception {
        var study = cpAcGoal();
        Path p = src("empty_cases.cpp", ECHO_DOUBLE);
        RunReport r = svc.runAll(p);
        assertEquals(0, r.passed());
        assertEquals(0, r.total());
        assertEquals(0.0, study.allGoalsProgress().get(0).current());
    }

    @Test
    void deleteCaseRemovesCase() throws Exception {
        Path p = src("del.cpp", ECHO_DOUBLE);
        var tc = svc.addCase(p, "1\n", "2\n");
        assertEquals(1, svc.cases(p).size());
        svc.deleteCase(tc.id());
        assertTrue(svc.cases(p).isEmpty());
    }
}
