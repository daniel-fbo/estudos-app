package br.estudos.app.ports;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Executa um processo externo. Base para compilação, execução de código e CLIs de IA. */
public interface ProcessRunner {

    /**
     * Roda o processo até terminar ou estourar o timeout (aí mata a árvore inteira).
     *
     * @throws IOException se o executável não puder ser iniciado
     */
    ProcessResult run(ProcessRequest request) throws IOException;

    record ProcessRequest(List<String> command, Path workDir, String stdin,
                          Duration timeout, Map<String, String> env) {
        public ProcessRequest {
            command = List.copyOf(command);
            env = env == null ? Map.of() : Map.copyOf(env);
            if (command.isEmpty()) throw new IllegalArgumentException("comando vazio");
            if (timeout == null || timeout.isNegative() || timeout.isZero())
                throw new IllegalArgumentException("timeout deve ser positivo");
        }

        public static ProcessRequest of(Duration timeout, String... command) {
            return new ProcessRequest(List.of(command), null, null, timeout, Map.of());
        }

        public ProcessRequest withStdin(String input) {
            return new ProcessRequest(command, workDir, input, timeout, env);
        }

        public ProcessRequest withWorkDir(Path dir) {
            return new ProcessRequest(command, dir, stdin, timeout, env);
        }
    }

    record ProcessResult(int exitCode, String stdout, String stderr,
                         Duration duration, boolean timedOut) {
        public boolean ok() {
            return !timedOut && exitCode == 0;
        }
    }
}
