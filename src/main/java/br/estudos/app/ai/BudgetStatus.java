package br.estudos.app.ai;

/** Uso de hoje. Respostas do cache não contam. */
public record BudgetStatus(int callsToday, long tokensToday, int dailyLimit) {
    /** A próxima chamada passaria do orçamento. */
    public boolean exceeded() {
        return callsToday >= dailyLimit;
    }
}
