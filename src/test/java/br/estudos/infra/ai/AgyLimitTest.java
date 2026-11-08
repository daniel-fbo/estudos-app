package br.estudos.infra.ai;

import br.estudos.app.ai.AiException;
import br.estudos.app.ports.ProcessRunner;
import br.estudos.app.tasks.CancellationToken;
import br.estudos.domain.AiRequest;
import br.estudos.domain.AiRunnerKind;
import br.estudos.domain.AiTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AgyLimitTest {
    @TempDir Path dir;

    @Test
    void promptAcimaDe100KbFalhaAntesDeExecutar() {
        AtomicInteger runs = new AtomicInteger();
        ProcessRunner p = r -> { runs.incrementAndGet(); return new ProcessRunner.ProcessResult(0, "", "", Duration.ZERO, false); };
        var agy = new AgyCliRunner(p, () -> "agy", () -> dir);
        var big = AiRequest.of(AiRunnerKind.AGY, null, AiTask.OUTRO, "x".repeat(AgyCliRunner.MAX_PROMPT_BYTES + 1));
        AiException e = assertThrows(AiException.class, () -> agy.run(big, CancellationToken.none()));
        assertTrue(e.getMessage().contains("Claude"));
        assertEquals(0, runs.get());
    }

    @Test
    void claudeAceitaPromptGrandePeloStdin() throws Exception {
        String json = "{\"type\":\"result\",\"is_error\":false,\"result\":\"OK\",\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}";
        var seen = new java.util.concurrent.atomic.AtomicReference<ProcessRunner.ProcessRequest>();
        ProcessRunner p = r -> { seen.set(r); return new ProcessRunner.ProcessResult(0, json, "", Duration.ZERO, false); };
        String big = "é".repeat(200_000);
        new ClaudeCliRunner(p, () -> "claude", () -> dir)
                .run(AiRequest.of(AiRunnerKind.CLAUDE, null, AiTask.OUTRO, big), CancellationToken.none());
        assertEquals(big, seen.get().stdin());
        assertFalse(seen.get().command().contains(big));
    }
}
