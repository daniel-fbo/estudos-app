package br.estudos.app.cf;

import java.util.List;
import java.util.Optional;

public interface CfRepository {
    /** Insere ou atualiza (por id). Devolve quantas eram novas. */
    int upsertSubmissions(List<CfSubmission> subs);

    List<CfSubmission> submissions();

    void replaceRatings(List<CfRatingPoint> points);

    List<CfRatingPoint> ratings();

    Optional<String> hint(String problemRef, int level);

    void saveHint(String problemRef, int level, String text, String createdAt);

    /** Maior nível salvo (0 se nenhum). */
    int maxHintLevel(String problemRef);
}
