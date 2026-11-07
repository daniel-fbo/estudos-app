package br.estudos.domain;

import java.time.Instant;

public record Goal(long id, String title, Area area, GoalKind kind, GoalPeriod period, double target,
                   int manualCount, int autoCountAc, boolean active, Instant createdAt) {

    public Goal withManualCount(int c) {
        return new Goal(id, title, area, kind, period, target, c, autoCountAc, active, createdAt);
    }
}
