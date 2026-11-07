package br.estudos.app.cf;

/** Controle de níveis da dica: cada nível só abre depois do anterior. */
public final class HintLevels {
    private HintLevels() {}

    public static boolean allowed(int level, int maxSaved) {
        return level >= 1 && level <= 3 && level <= maxSaved + 1;
    }

    public static String instruction(int level) {
        return switch (level) {
            case 1 -> "Dê apenas o TEMA: em que área/técnica o problema se encaixa, em 1 ou 2 frases, sem revelar a solução.";
            case 2 -> "Dê a OBSERVAÇÃO-CHAVE que destrava o problema, em poucas frases, sem descrever o algoritmo completo.";
            case 3 -> "Dê um ESBOÇO da solução em passos numerados e a complexidade esperada, em prosa.";
            default -> throw new IllegalArgumentException("nível inválido: " + level);
        };
    }
}
