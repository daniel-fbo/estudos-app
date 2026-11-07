package br.estudos.infra.ai;

import br.estudos.app.ai.AiException;
import br.estudos.app.ports.ProcessRunner;
import br.estudos.app.tasks.CancellationToken;
import br.estudos.domain.AiRequest;
import br.estudos.domain.AiResult;
import br.estudos.domain.AiRunnerKind;
import br.estudos.domain.AiTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CliRunnersTest {
    @TempDir Path dir;

    static final String CLAUDE_OUT = """
            {"type":"result","subtype":"success","is_error":false,"result":"OK","total_cost_usd":0.0042,
             "usage":{"input_tokens":2,"cache_creation_input_tokens":20,"cache_read_input_tokens":8,"output_tokens":4}}""";
    static final String AGY_OUT = """
            {"conversation_id":"x","status":"SUCCESS","response":"OK\\n","usage":{"input_tokens":6870,
             "output_tokens":89,"thinking_tokens":88,"cache_read_tokens":0,"total_tokens":6959}}""";

    static class FakeProcesses implements ProcessRunner {
        final AtomicReference<ProcessRequest> last = new AtomicReference<>();
        ProcessResult result;
        FakeProcesses(ProcessResult r) { result = r; }
        public ProcessResult run(ProcessRequest r) { last.set(r); return result; }
    }

    static ProcessRunner.ProcessResult ok(String out) {
        return new ProcessRunner.ProcessResult(0, out, "", Duration.ofSeconds(1), false);
    }

    @Test
    void claudeBuildsCommandInRepoDirAndParsesJson() throws Exception {
        FakeProcesses p = new FakeProcesses(ok(CLAUDE_OUT));
        var r = new ClaudeCliRunner(p, () -> "/bin/claude", () -> dir);
        AiResult res = r.run(AiRequest.of(AiRunnerKind.CLAUDE, "haiku", AiTask.OUTRO, "oi")
                .withTimeout(Duration.ofSeconds(30)), CancellationToken.none());
        assertEquals(List.of("/bin/claude", "-p", "oi", "--output-format", "json", "--model", "haiku",
                "--dangerously-skip-permissions"), p.last.get().command());
        assertEquals(dir, p.last.get().workDir());
        assertEquals(Duration.ofSeconds(30), p.last.get().timeout());
        assertEquals(new AiResult("OK", 30L, 4L, 0.0042), res);
    }

    @Test
    void agyBuildsCommandAndParsesJson() throws Exception {
        FakeProcesses p = new FakeProcesses(ok(AGY_OUT));
        var r = new AgyCliRunner(p, () -> "agy", () -> dir);
        AiResult res = r.run(AiRequest.of(AiRunnerKind.AGY, null, AiTask.OUTRO, "oi"), CancellationToken.none());
        assertEquals(List.of("agy", "--dangerously-skip-permissions", "--output-format", "json", "--print", "oi"),
                p.last.get().command());
        assertEquals(new AiResult("OK", 6870L, 177L, null), res);
        assertTrue(r.supported());
    }

    @Test
    void failuresBecomeAiException() {
        var req = AiRequest.of(AiRunnerKind.CLAUDE, null, AiTask.OUTRO, "oi");
        var timeout = new FakeProcesses(new ProcessRunner.ProcessResult(-1, "", "", Duration.ofMinutes(10), true));
        assertTrue(assertThrows(AiException.class, () -> new ClaudeCliRunner(timeout, () -> "c", () -> dir)
                .run(req, CancellationToken.none())).getMessage().contains("tempo esgotado"));

        var exit = new FakeProcesses(new ProcessRunner.ProcessResult(1, "", "not logged in", Duration.ZERO, false));
        assertTrue(assertThrows(AiException.class, () -> new ClaudeCliRunner(exit, () -> "c", () -> dir)
                .run(req, CancellationToken.none())).getMessage().contains("not logged in"));

        var missingDir = new ClaudeCliRunner(new FakeProcesses(ok(CLAUDE_OUT)), () -> "c", () -> dir.resolve("nao"));
        assertTrue(assertThrows(AiException.class, () -> missingDir.run(req, CancellationToken.none()))
                .getMessage().contains("estudos"));
    }

    @Test
    void parserRejectsErrorsAndGarbage() {
        assertThrows(AiException.class, () -> CliOutputParser.claude("""
                {"subtype":"error_max_turns","is_error":true,"result":"x"}"""));
        assertThrows(AiException.class, () -> CliOutputParser.claude("not json"));
        assertThrows(AiException.class, () -> CliOutputParser.agy("{\"status\":\"ERROR\",\"response\":\"x\"}"));
        // avisos antes do JSON são ignorados; campos ausentes viram null
        assertDoesNotThrow(() -> {
            AiResult r = CliOutputParser.claude("aviso\n{\"result\":\"oi\"}");
            assertEquals(new AiResult("oi", null, null, null), r);
        });
    }

    @Test
    void claudeParserHandlesEmptyResultAndMissingFields() throws Exception {
        AiResult empty = CliOutputParser.claude("{\"result\":\"\"}");
        assertEquals("", empty.response());
        assertNull(empty.inputTokens());
        assertNull(empty.outputTokens());
        assertNull(empty.costUsd());

        var ex = assertThrows(AiException.class, () -> CliOutputParser.claude("{\"is_error\":false}"));
        assertTrue(ex.getMessage().contains("sem campo 'result'"));
    }

    @Test
    void agyParserHandlesWhitespaceAndTokens() throws Exception {
        String json = """
                {"status":"SUCCESS","response":"  resultado com espaços  \\n",
                 "usage":{"input_tokens":100,"cache_read_tokens":50,"output_tokens":20,"thinking_tokens":10}}
                """;
        AiResult r = CliOutputParser.agy(json);
        assertEquals("resultado com espaços", r.response());
        assertEquals(150L, r.inputTokens());
        assertEquals(30L, r.outputTokens());
        assertNull(r.costUsd());

        assertThrows(AiException.class, () -> CliOutputParser.agy("{\"status\":\"TIMEOUT\",\"response\":\"x\"}"));
        assertThrows(AiException.class, () -> CliOutputParser.agy("{\"status\":\"SUCCESS\"}"));
    }

    @Test
    void preCancelledTokenThrowsImmediatelyWithoutCallingProcess() {
        FakeProcesses p = new FakeProcesses(ok(CLAUDE_OUT));
        var r = new ClaudeCliRunner(p, () -> "claude", () -> dir);
        CancellationToken tok = new CancellationToken();
        tok.cancel();

        assertThrows(java.util.concurrent.CancellationException.class,
                () -> r.run(AiRequest.of(AiRunnerKind.CLAUDE, null, AiTask.OUTRO, "oi"), tok));
        assertNull(p.last.get());
    }

    @Test
    void abbreviateTruncatesLongStrings() {
        String longText = "a".repeat(400);
        String abbr = CliOutputParser.abbreviate(longText);
        assertEquals(301, abbr.length());
        assertTrue(abbr.endsWith("…"));
    }

    @Test
    void tokenCancellationAfterCompletionMustNotInterruptWorkerThread() throws Exception {
        FakeProcesses p = new FakeProcesses(ok(CLAUDE_OUT));
        var r = new ClaudeCliRunner(p, () -> "/bin/claude", () -> dir);
        CancellationToken token = new CancellationToken();
        r.run(AiRequest.of(AiRunnerKind.CLAUDE, "haiku", AiTask.OUTRO, "oi"), token);
        assertFalse(Thread.currentThread().isInterrupted());

        // Cancel the token after completion
        token.cancel();
        boolean interrupted = Thread.interrupted();
        assertFalse(interrupted, "Worker thread should not be interrupted after task completes");
    }
}
