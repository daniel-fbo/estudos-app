package br.estudos.infra.process;

import br.estudos.app.ports.ProcessRunner;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** {@link ProcessRunner} real: lê stdout/stderr em paralelo e, no timeout, mata a árvore de processos. */
public class SystemProcessRunner implements ProcessRunner {

    /** Quanto esperar pelos streams depois que o processo terminou ou foi morto. */
    private static final long DRAIN_SECONDS = 5;

    @Override
    public ProcessResult run(ProcessRequest req) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(req.command());
        if (req.workDir() != null) pb.directory(req.workDir().toFile());
        pb.environment().putAll(req.env());

        long start = System.nanoTime();
        Process p = pb.start();
        CompletableFuture<String> out = drain(p.getInputStream());
        CompletableFuture<String> err = drain(p.getErrorStream());
        feedStdin(p, req.stdin());

        boolean timedOut;
        try {
            timedOut = !p.waitFor(req.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (timedOut) {
                killTree(p);
                p.waitFor(DRAIN_SECONDS, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            killTree(p);
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("execução interrompida: " + req.command().getFirst());
        }
        Duration duration = Duration.ofNanos(System.nanoTime() - start);
        int exit = p.isAlive() ? -1 : p.exitValue();
        return new ProcessResult(exit, await(out), await(err), duration, timedOut);
    }

    private static void killTree(Process p) {
        List<ProcessHandle> descendants = p.descendants().toList();
        descendants.forEach(ProcessHandle::destroyForcibly);
        p.destroyForcibly();
    }

    private static CompletableFuture<String> drain(InputStream in) {
        CompletableFuture<String> f = new CompletableFuture<>();
        Thread.ofVirtual().start(() -> {
            try (in) {
                f.complete(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                f.complete("");
            }
        });
        return f;
    }

    /** Escreve o stdin numa thread própria: um processo que não lê stdin não pode travar o timeout. */
    private static void feedStdin(Process p, String stdin) {
        OutputStream os = p.getOutputStream();
        Thread.ofVirtual().start(() -> {
            try (os) {
                if (stdin != null) os.write(stdin.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // o processo fechou o stdin antes de ler tudo; não é erro nosso
            }
        });
    }

    private static String await(CompletableFuture<String> f) {
        try {
            return f.get(DRAIN_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } catch (Exception e) {
            return "";
        }
    }
}
