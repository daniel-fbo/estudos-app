package br.estudos.domain;

import java.time.Instant;

/**
 * Meta de estudo.
 *
 * @param source fonte automática (coluna {@code auto_count_ac}); só tem efeito em metas de CONTAGEM
 *               (ver {@link GoalSource#allowedFor})
 */
public record Goal(long id, String title, Area area, GoalKind kind, GoalPeriod period, double target,
                   GoalSource source, boolean active, Instant createdAt) {

    public Goal withActive(boolean a) {
        return new Goal(id, title, area, kind, period, target, source, a, createdAt);
    }

    public Goal withSource(GoalSource s) {
        return new Goal(id, title, area, kind, period, target, s, active, createdAt);
    }

    public Goal withTitleTarget(String t, double tg) {
        return new Goal(id, t, area, kind, period, tg, source, active, createdAt);
    }

    /** Fonte efetiva: {@link GoalSource#NENHUMA} se a combinação área/tipo não a permite. */
    public GoalSource effectiveSource() {
        return source.allowedFor(area, kind) ? source : GoalSource.NENHUMA;
    }
}
