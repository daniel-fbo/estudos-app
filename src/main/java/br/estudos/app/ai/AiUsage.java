package br.estudos.app.ai;

import br.estudos.domain.AiCall;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/** Agregados para a tela Uso de IA. Puro. */
public record AiUsage(Map<String, Row> byDay, Map<String, Row> byTask, Map<String, Row> byModel,
                      long cacheHits, long realCalls) {

    /** Chamadas reais (sem cache), tokens e custo de um grupo. Custo é {@code null} se nenhuma linha informou. */
    public record Row(long calls, long cacheHits, long inputTokens, long outputTokens, Double costUsd) {
        Row add(AiCall c) {
            if (c.cacheHit()) return new Row(calls, cacheHits + 1, inputTokens, outputTokens, costUsd);
            Double cost = c.costUsd() == null ? costUsd : (costUsd == null ? 0 : costUsd) + c.costUsd();
            return new Row(calls + 1, cacheHits,
                    inputTokens + (c.inputTokens() == null ? 0 : c.inputTokens()),
                    outputTokens + (c.outputTokens() == null ? 0 : c.outputTokens()), cost);
        }
    }

    private static final Row EMPTY = new Row(0, 0, 0, 0, null);

    /** Fração de pedidos atendidos pelo cache (0 se não houve pedido). */
    public double cacheHitRate() {
        long total = cacheHits + realCalls;
        return total == 0 ? 0 : (double) cacheHits / total;
    }

    public static AiUsage of(List<AiCall> calls, ZoneId zone) {
        Map<String, Row> day = group(calls, c -> LocalDate.ofInstant(c.createdAt(), zone).toString());
        Map<String, Row> task = group(calls, c -> c.task().name());
        Map<String, Row> model = group(calls, c -> c.runner() + " " + (c.model() == null ? "(padrão)" : c.model()));
        long hits = calls.stream().filter(AiCall::cacheHit).count();
        return new AiUsage(day, task, model, hits, calls.size() - hits);
    }

    private static Map<String, Row> group(List<AiCall> calls, Function<AiCall, String> key) {
        Map<String, Row> m = new TreeMap<>();
        for (AiCall c : calls) m.merge(key.apply(c), EMPTY.add(c), (a, b) -> a.add(c));
        return m;
    }
}
