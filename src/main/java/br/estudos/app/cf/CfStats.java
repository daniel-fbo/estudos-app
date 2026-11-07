package br.estudos.app.cf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;

/** Agregações puras sobre submissões. */
public final class CfStats {
    private CfStats() {}

    public record TagStat(String tag, int total, int accepted, int failed) {
        public double acRate() { return total == 0 ? 0 : (double) accepted / total; }
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Converte a resposta de {@code user.status} em submissões (ignora itens sem id/data). */
    public static List<CfSubmission> parseStatus(String json) throws CfException {
        try {
            JsonNode root = JSON.readTree(json);
            if (!"OK".equals(root.path("status").asText()))
                throw new CfException("Codeforces: " + root.path("comment").asText("resposta com erro"));
            List<CfSubmission> out = new ArrayList<>();
            for (JsonNode n : root.path("result")) {
                if (!n.has("id") || !n.has("creationTimeSeconds")) continue;
                JsonNode p = n.path("problem");
                List<String> tags = new ArrayList<>();
                for (JsonNode t : p.path("tags")) tags.add(t.asText());
                out.add(new CfSubmission(n.get("id").asLong(),
                        p.has("contestId") ? p.get("contestId").asInt() : null,
                        p.path("index").asText(null), p.path("name").asText(null),
                        p.has("rating") ? p.get("rating").asInt() : null, tags,
                        n.path("verdict").asText(null),
                        java.time.Instant.ofEpochSecond(n.get("creationTimeSeconds").asLong()).toString()));
            }
            return out;
        } catch (IOException e) {
            throw new CfException("JSON inválido do Codeforces: " + e.getMessage(), e);
        }
    }

    public static List<CfRatingPoint> parseRating(String json) throws CfException {
        try {
            JsonNode root = JSON.readTree(json);
            if (!"OK".equals(root.path("status").asText()))
                throw new CfException("Codeforces: " + root.path("comment").asText("resposta com erro"));
            List<CfRatingPoint> out = new ArrayList<>();
            for (JsonNode n : root.path("result"))
                out.add(new CfRatingPoint(n.path("contestId").asInt(), n.path("contestName").asText(""),
                        java.time.Instant.ofEpochSecond(n.path("ratingUpdateTimeSeconds").asLong()).toString(),
                        n.path("oldRating").asInt(), n.path("newRating").asInt()));
            return out;
        } catch (IOException e) {
            throw new CfException("JSON inválido do Codeforces: " + e.getMessage(), e);
        }
    }

    /** Estatísticas por tag (uma submissão conta em todas as suas tags), ordenadas por total desc. */
    public static List<TagStat> byTag(List<CfSubmission> subs) {
        Map<String, int[]> m = new TreeMap<>();
        for (CfSubmission s : subs) {
            if (s.verdict() == null) continue;
            for (String t : s.tags()) {
                int[] a = m.computeIfAbsent(t, k -> new int[3]);
                if (s.accepted()) { a[0]++; a[1]++; }
                else if (s.failed()) { a[0]++; a[2]++; }
            }
        }
        List<TagStat> out = new ArrayList<>();
        m.forEach((t, a) -> { if (a[0] > 0) out.add(new TagStat(t, a[0], a[1], a[2])); });
        out.sort(Comparator.comparingInt(TagStat::total).reversed().thenComparing(TagStat::tag));
        return out;
    }

    /** Tags com mais WA/TLE etc. (falhas), maior primeiro. */
    public static List<TagStat> mostFailed(List<CfSubmission> subs) {
        return byTag(subs).stream().filter(t -> t.failed() > 0)
                .sorted(Comparator.comparingInt(TagStat::failed).reversed().thenComparing(TagStat::tag)).toList();
    }

    /**
     * "Treine estas N tags": tags com pelo menos {@code minTotal} tentativas, pior taxa de AC primeiro
     * (desempate: mais falhas, depois nome).
     */
    public static List<String> suggest(List<CfSubmission> subs, int n, int minTotal) {
        return byTag(subs).stream().filter(t -> t.total() >= minTotal && t.failed() > 0)
                .sorted(Comparator.comparingDouble(TagStat::acRate)
                        .thenComparing(Comparator.comparingInt(TagStat::failed).reversed())
                        .thenComparing(TagStat::tag))
                .limit(n).map(TagStat::tag).toList();
    }
}
