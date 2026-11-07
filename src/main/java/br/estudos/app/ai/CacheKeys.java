package br.estudos.app.ai;

import br.estudos.domain.AiRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** {@code sha256(runner + modelo + tarefa + prompt + hash dos anexos)}. */
public final class CacheKeys {
    private CacheKeys() {}

    public static String of(AiRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(req.runner()).append('\n')
          .append(req.model() == null ? "" : req.model()).append('\n')
          .append(req.task()).append('\n')
          .append(req.prompt()).append('\n');
        for (Path p : req.attachments()) {
            sb.append(p.getFileName()).append(':').append(sha256(p)).append('\n');
        }
        return sha256(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(Path file) throws IOException {
        try (java.io.InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
