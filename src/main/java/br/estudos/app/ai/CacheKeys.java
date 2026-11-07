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
            sb.append(p.getFileName()).append(':').append(sha256(Files.readAllBytes(p))).append('\n');
        }
        return sha256(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
