package br.estudos.infra.ai;

import br.estudos.app.ai.AiException;
import br.estudos.domain.AiResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Lê o JSON de {@code --output-format json} de cada CLI. */
public final class CliOutputParser {
    private static final ObjectMapper JSON = new ObjectMapper();

    private CliOutputParser() {}

    /**
     * Claude: {@code result}, {@code is_error}, {@code total_cost_usd}, {@code usage.*}. Tokens de entrada somam
     * os de cache (criação e leitura), que também são processados.
     */
    public static AiResult claude(String stdout) throws AiException {
        JsonNode n = parse(stdout);
        String result = text(n, "result");
        if (n.path("is_error").asBoolean(false) || !"success".equals(n.path("subtype").asText("success")))
            throw new AiException("claude devolveu erro: " + (result == null ? n.path("subtype").asText() : result));
        if (result == null) throw new AiException("claude: JSON sem campo 'result'");
        JsonNode u = n.path("usage");
        Long in = sum(u, "input_tokens", "cache_creation_input_tokens", "cache_read_input_tokens");
        Long out = sum(u, "output_tokens");
        Double cost = n.hasNonNull("total_cost_usd") ? n.get("total_cost_usd").asDouble() : null;
        return new AiResult(result, in, out, cost);
    }

    /** Agy: {@code status} (SUCCESS), {@code response}, {@code usage.input_tokens/output_tokens/thinking_tokens}. */
    public static AiResult agy(String stdout) throws AiException {
        JsonNode n = parse(stdout);
        String status = n.path("status").asText("");
        String response = text(n, "response");
        if (!"SUCCESS".equalsIgnoreCase(status))
            throw new AiException("agy devolveu status " + status + (response == null ? "" : ": " + response));
        if (response == null) throw new AiException("agy: JSON sem campo 'response'");
        JsonNode u = n.path("usage");
        return new AiResult(response.strip(), sum(u, "input_tokens", "cache_read_tokens"),
                sum(u, "output_tokens", "thinking_tokens"), null);
    }

    private static JsonNode parse(String stdout) throws AiException {
        String s = stdout == null ? "" : stdout.strip();
        // algumas versões imprimem avisos antes do JSON: pega da primeira '{' em diante
        int i = s.indexOf('{');
        if (i < 0) throw new AiException("saída sem JSON: " + abbreviate(s));
        try {
            return JSON.readTree(s.substring(i));
        } catch (Exception e) {
            throw new AiException("JSON inválido na saída: " + abbreviate(s), e);
        }
    }

    private static String text(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }

    /** Soma os campos presentes; {@code null} se nenhum existir. */
    private static Long sum(JsonNode u, String... fields) {
        Long total = null;
        for (String f : fields) {
            if (u.hasNonNull(f)) total = (total == null ? 0 : total) + u.get(f).asLong();
        }
        return total;
    }

    static String abbreviate(String s) {
        return s.length() <= 300 ? s : s.substring(0, 300) + "…";
    }
}
