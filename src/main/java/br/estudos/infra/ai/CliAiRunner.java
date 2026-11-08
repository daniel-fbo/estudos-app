package br.estudos.infra.ai;

import br.estudos.app.ai.AiException;
import br.estudos.app.ports.AiRunner;
import br.estudos.app.ports.ProcessRunner;
import br.estudos.app.ports.ProcessRunner.ProcessRequest;
import br.estudos.app.ports.ProcessRunner.ProcessResult;
import br.estudos.app.tasks.CancellationToken;
import br.estudos.domain.AiRequest;
import br.estudos.domain.AiResult;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

/**
 * Base das CLIs: roda no diretório do repositório {@code estudos}. O cancelamento interrompe a thread,
 * e o {@link ProcessRunner} mata a árvore de processos.
 */
abstract class CliAiRunner implements AiRunner {

    private final ProcessRunner processes;
    private final Supplier<String> executable;
    private final Supplier<Path> workDir;

    CliAiRunner(ProcessRunner processes, Supplier<String> executable, Supplier<Path> workDir) {
        this.processes = processes;
        this.executable = executable;
        this.workDir = workDir;
    }

    abstract List<String> arguments(AiRequest req);

    /** Texto enviado pelo stdin, ou {@code null}. */
    String stdin(AiRequest req) { return null; }

    /** Falha antes de executar se o pedido não puder ser atendido por esta CLI. */
    void validate(AiRequest req) throws AiException { }

    abstract AiResult parse(String stdout) throws AiException;

    @Override
    public AiResult run(AiRequest req, CancellationToken token) throws AiException {
        token.throwIfCancelled();
        validate(req);
        Path dir = workDir.get();
        if (dir == null || !Files.isDirectory(dir))
            throw new AiException("Repositório 'estudos' não encontrado: " + dir + " (ajuste em Configurações).");
        List<String> cmd = new java.util.ArrayList<>();
        cmd.add(executable.get());
        cmd.addAll(arguments(req));
        ProcessRequest pr = new ProcessRequest(cmd, dir, stdin(req), req.timeout(), Map.of());

        Thread worker = Thread.currentThread();
        java.util.concurrent.atomic.AtomicBoolean running = new java.util.concurrent.atomic.AtomicBoolean(true);
        token.onCancel(() -> { if (running.get()) worker.interrupt(); });
        ProcessResult res;
        try {
            res = processes.run(pr);
        } catch (InterruptedIOException e) {
            throw new CancellationException("cancelado");
        } catch (IOException e) {
            throw new AiException("Não foi possível executar " + cmd.getFirst() + ": " + e.getMessage(), e);
        } finally {
            running.set(false);
        }
        token.throwIfCancelled();
        if (res.timedOut())
            throw new AiException(kind() + ": tempo esgotado (" + req.timeout().toMinutes() + " min)");
        if (res.exitCode() != 0) {
            String detail = res.stderr().isBlank() ? res.stdout() : res.stderr();
            throw new AiException(kind() + " saiu com código " + res.exitCode() + ": "
                    + CliOutputParser.abbreviate(detail.strip()));
        }
        return parse(res.stdout());
    }
}
