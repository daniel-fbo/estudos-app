package br.estudos.app.anki;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Extrai blocos {@code > **Card.** pergunta / > --- / > resposta} de um Markdown. */
public final class CardMarkdownParser {
    private CardMarkdownParser() {}

    private static final String MARK = "**Card.**";

    public static List<CardDraft> parse(String markdown) {
        List<CardDraft> out = new ArrayList<>();
        List<String> block = new ArrayList<>();
        for (String line : markdown.split("\r?\n", -1)) {
            String t = line.stripLeading();
            if (t.startsWith(">")) {
                t = t.substring(1);
                if (t.startsWith(" ")) t = t.substring(1);
                block.add(t);
            } else {
                flush(block, out);
            }
        }
        flush(block, out);
        return out;
    }

    private static void flush(List<String> block, List<CardDraft> out) {
        if (block.isEmpty()) return;
        // um bloco de citação pode conter mais de um card (separados por linha vazia ">")
        StringBuilder front = null, back = null;
        boolean inBack = false;
        List<String> lines = new ArrayList<>(block);
        lines.add(null);
        for (String l : lines) {
            boolean start = l != null && l.stripLeading().startsWith(MARK);
            if (l == null || start) {
                if (front != null && back != null) add(out, front, back);
                front = back = null;
                inBack = false;
                if (l == null) break;
                front = new StringBuilder(l.stripLeading().substring(MARK.length()).strip());
                continue;
            }
            if (front == null) continue;
            if (!inBack && l.strip().equals("---")) {
                inBack = true;
                back = new StringBuilder();
            } else if (inBack) {
                back.append(back.length() == 0 ? "" : "\n").append(l);
            } else {
                front.append('\n').append(l);
            }
        }
        block.clear();
    }

    private static void add(List<CardDraft> out, StringBuilder f, StringBuilder b) {
        String front = f.toString().strip(), back = b.toString().strip();
        if (!front.isEmpty() && !back.isEmpty()) out.add(new CardDraft(front, back));
    }

    /** SHA-256 da frente normalizada (minúsculas, espaços colapsados). */
    public static String hash(String front) {
        String n = front.strip().toLowerCase().replaceAll("\\s+", " ");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(n.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
