package br.estudos.infra.ai;

import br.estudos.app.tasks.CancellationToken;
import br.estudos.domain.AiRequest;
import br.estudos.domain.AiResult;
import br.estudos.domain.AiRunnerKind;
import br.estudos.domain.AiTask;
import br.estudos.infra.process.SystemProcessRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/** Teste manual com as CLIs reais (gasta tokens). Rodar com {@code AI_REAL=1 mvn test -Dtest=RealCliManualTest}. */
@EnabledIfEnvironmentVariable(named = "AI_REAL", matches = "1")
class RealCliManualTest {
    @TempDir Path dir;

    @Test
    void claude() throws Exception {
        AiResult r = new ClaudeCliRunner(new SystemProcessRunner(), () -> "claude", () -> dir)
                .run(AiRequest.of(AiRunnerKind.CLAUDE, "haiku", AiTask.OUTRO, "Responda só OK")
                        .withTimeout(Duration.ofMinutes(2)), CancellationToken.none());
        System.out.println("claude: " + r);
        assertTrue(r.response().contains("OK"));
    }

    @Test
    void agy() throws Exception {
        AiResult r = new AgyCliRunner(new SystemProcessRunner(), () -> "agy", () -> dir)
                .run(AiRequest.of(AiRunnerKind.AGY, null, AiTask.OUTRO, "Responda só OK")
                        .withTimeout(Duration.ofMinutes(2)), CancellationToken.none());
        System.out.println("agy: " + r);
        assertTrue(r.response().contains("OK"));
    }
}
