package br.estudos.app.code;

import java.util.ArrayList;
import java.util.List;

/** Compara saídas ignorando espaço no fim de linha e linhas vazias no final. */
public final class OutputComparator {
    private OutputComparator() {}

    public static List<String> normalize(String s) {
        List<String> lines = new ArrayList<>(List.of((s == null ? "" : s).replace("\r\n", "\n").split("\n", -1)));
        lines.replaceAll(l -> l.stripTrailing());
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) lines.remove(lines.size() - 1);
        return lines;
    }

    public static boolean equal(String expected, String actual) {
        return normalize(expected).equals(normalize(actual));
    }

    /** Diff linha a linha; vazio se iguais. */
    public static String diff(String expected, String actual) {
        List<String> e = normalize(expected), a = normalize(actual);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.max(e.size(), a.size()); i++) {
            String x = i < e.size() ? e.get(i) : null, y = i < a.size() ? a.get(i) : null;
            if (java.util.Objects.equals(x, y)) continue;
            sb.append("linha ").append(i + 1).append(": esperado ")
              .append(x == null ? "(nada)" : "[" + x + "]").append(", obtido ")
              .append(y == null ? "(nada)" : "[" + y + "]").append('\n');
        }
        return sb.toString();
    }
}
