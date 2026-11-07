package br.estudos.domain;

/** Fonte automática de uma meta de CONTAGEM (coluna {@code goal.auto_count_ac}). Uma meta tem uma só. */
public enum GoalSource {
    /** Só o contador manual. */
    NENHUMA(0, "Nenhuma (só manual)"),
    /** Arquivos com primeiro AC no runner de código (só CP). */
    RUNNER(1, "ACs do runner"),
    /** Problemas distintos com primeiro OK no Codeforces (só CP). */
    CODEFORCES(2, "ACs do Codeforces"),
    /** Páginas lidas nos livros da mesma área. */
    PAGINAS(3, "Páginas lidas");

    private final int code;
    private final String label;

    GoalSource(int code, String label) { this.code = code; this.label = label; }

    public int code() { return code; }

    @Override public String toString() { return label; }

    public static GoalSource of(int code) {
        for (GoalSource s : values()) if (s.code == code) return s;
        return NENHUMA;
    }

    /** Se a fonte vale para uma meta desta área e tipo. */
    public boolean allowedFor(Area area, GoalKind kind) {
        if (this == NENHUMA) return true;
        if (kind != GoalKind.CONTAGEM) return false;
        return this == PAGINAS || area == Area.CP;
    }
}
