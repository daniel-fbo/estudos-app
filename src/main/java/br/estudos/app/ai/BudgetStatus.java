package br.estudos.app.ai;

/** Uso de hoje. Respostas do cache não contam. {@code tokenLimit == 0} = sem limite de tokens. */
public record BudgetStatus(int callsToday, long tokensToday, int dailyLimit, long tokenLimit) {

    public BudgetStatus(int callsToday, long tokensToday, int dailyLimit) {
        this(callsToday, tokensToday, dailyLimit, 0);
    }

    public BudgetStatus withTokenLimit(long limit) {
        return new BudgetStatus(callsToday, tokensToday, dailyLimit, limit);
    }

    public boolean callsExceeded() {
        return callsToday >= dailyLimit;
    }

    public boolean tokensExceeded() {
        return tokenLimit > 0 && tokensToday >= tokenLimit;
    }

    /** A próxima chamada passaria do orçamento (de chamadas ou de tokens). */
    public boolean exceeded() {
        return callsExceeded() || tokensExceeded();
    }

    /** Diz qual limite estourou (ou os dois). */
    public String reason() {
        String c = "limite de chamadas (" + callsToday + "/" + dailyLimit + ")";
        String t = "limite de tokens (" + tokensToday + "/" + tokenLimit + ")";
        if (callsExceeded() && tokensExceeded()) return c + " e " + t;
        if (tokensExceeded()) return t;
        return c;
    }
}
