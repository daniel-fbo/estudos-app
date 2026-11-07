package br.estudos.infra.process;

import br.estudos.app.ports.ProcessRunner.ProcessRequest;
import br.estudos.app.ports.ProcessRunner.ProcessResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class SystemProcessRunnerTest {

    private final SystemProcessRunner runner = new SystemProcessRunner();

    @Test
    void capturesStdoutStderrAndExitCode() throws Exception {
        ProcessResult r = runner.run(ProcessRequest.of(Duration.ofSeconds(5),
                "sh", "-c", "echo saída; echo erro >&2; exit 3"));
        assertEquals(3, r.exitCode());
        assertEquals("saída\n", r.stdout());
        assertEquals("erro\n", r.stderr());
        assertFalse(r.timedOut());
    }

    @Test
    void passesStdin() throws Exception {
        ProcessResult r = runner.run(ProcessRequest.of(Duration.ofSeconds(5), "cat").withStdin("1 2\n3"));
        assertTrue(r.ok());
        assertEquals("1 2\n3", r.stdout());
    }

    @Test
    void killsWholeTreeOnTimeout() throws Exception {
        long start = System.nanoTime();
        // o filho "sleep" herda os pipes; se só o sh morresse, a leitura ficaria presa
        ProcessResult r = runner.run(ProcessRequest.of(Duration.ofMillis(300),
                "sh", "-c", "sleep 30; echo nunca"));
        assertTrue(r.timedOut());
        assertFalse(r.ok());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 5);
    }

    @Test
    void missingExecutableThrows() {
        assertThrows(IOException.class, () -> runner.run(
                ProcessRequest.of(Duration.ofSeconds(1), "/nao/existe/programa")));
    }
}
