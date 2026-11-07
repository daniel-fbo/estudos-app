package br.estudos.app.cf;

import java.util.List;

/** Submissão do Codeforces. {@code submittedAt} em ISO-8601 UTC; {@code rating} pode ser nulo. */
public record CfSubmission(long id, Integer contestId, String problemIndex, String problemName,
                           Integer rating, List<String> tags, String verdict, String submittedAt) {
    public CfSubmission {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public boolean accepted() { return "OK".equals(verdict); }

    /** Veredito que conta como falha de solução (WA/TLE/MLE/RE...), ignorando CE e testes. */
    public boolean failed() {
        return verdict != null && !accepted() && !verdict.equals("COMPILATION_ERROR")
                && !verdict.equals("TESTING") && !verdict.equals("SKIPPED") && !verdict.equals("CHALLENGED")
                && !verdict.equals("REJECTED") && !verdict.equals("PARTIAL");
    }

    public String problemKey() { return contestId + problemIndex; }
}
