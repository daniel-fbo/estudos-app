package br.estudos.app.study;

import br.estudos.domain.Goal;

/** Progresso de uma meta no período atual. {@code current} em horas (HORAS) ou unidades (CONTAGEM). */
public record GoalProgress(Goal goal, double current, double target) {
    /** Fração de 0 a 1 (limitada). */
    public double fraction() {
        return target <= 0 ? 0 : Math.max(0, Math.min(1, current / target));
    }

    public boolean done() {
        return current >= target;
    }
}
