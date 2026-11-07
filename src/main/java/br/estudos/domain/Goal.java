package br.estudos.domain;

import java.time.Instant;

/**
 * Meta de estudo.
 *
 * @param countAc se a meta conta automaticamente os ACs do runner de código (coluna {@code auto_count_ac});
 *                só tem efeito em metas de CONTAGEM da área CP
 */
public record Goal(long id, String title, Area area, GoalKind kind, GoalPeriod period, double target,
                   int manualCount, boolean countAc, boolean active, Instant createdAt) {

    public Goal withManualCount(int c) {
        return new Goal(id, title, area, kind, period, target, c, countAc, active, createdAt);
    }

    public Goal withActive(boolean a) {
        return new Goal(id, title, area, kind, period, target, manualCount, countAc, a, createdAt);
    }

    public Goal withCountAc(boolean c) {
        return new Goal(id, title, area, kind, period, target, manualCount, c, active, createdAt);
    }

    /** Se os ACs do runner entram no progresso desta meta. */
    public boolean countsAc() {
        return countAc && kind == GoalKind.CONTAGEM && area == Area.CP;
    }
}
