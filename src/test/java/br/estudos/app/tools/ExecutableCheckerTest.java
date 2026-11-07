package br.estudos.app.tools;

import br.estudos.app.ports.ProcessRunner;
import br.estudos.app.ports.ProcessRunner.ProcessResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ExecutableCheckerTest {

    private static ProcessRunner fake(ProcessResult result) {
        return req -> result;
    }

    @Test
    void okShowsFirstLineAndCallsVersion() {
        AtomicReference<List<String>> cmd = new AtomicReference<>();
        ProcessRunner runner = req -> {
            cmd.set(req.command());
            return new ProcessResult(0, "\ngcc (GCC) 16.2.1\nCopyright", "", Duration.ZERO, false);
        };

        var r = new ExecutableChecker(runner).check("  gcc ");

        assertTrue(r.ok());
        assertEquals("gcc (GCC) 16.2.1", r.message());
        assertEquals(List.of("gcc", "--version"), cmd.get());
    }

    @Test
    void usesStderrWhenStdoutEmpty() {
        var r = new ExecutableChecker(fake(new ProcessResult(0, "", "java 21", Duration.ZERO, false)))
                .check("java");
        assertEquals("java 21", r.message());
    }

    @Test
    void reportsFailureTimeoutAndMissing() {
        assertFalse(new ExecutableChecker(fake(new ProcessResult(2, "", "bad", Duration.ZERO, false)))
                .check("x").ok());
        assertTrue(new ExecutableChecker(fake(new ProcessResult(-1, "", "", Duration.ZERO, true)))
                .check("x").message().startsWith("Tempo esgotado"));
        ProcessRunner missing = req -> { throw new IOException("No such file"); };
        assertTrue(new ExecutableChecker(missing).check("x").message().contains("No such file"));
        assertFalse(new ExecutableChecker(missing).check(" ").ok());
    }
}
