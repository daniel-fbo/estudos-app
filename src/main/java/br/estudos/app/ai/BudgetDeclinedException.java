package br.estudos.app.ai;

/** O orçamento diário estourou e o usuário não confirmou a chamada. */
public class BudgetDeclinedException extends RuntimeException {
    public BudgetDeclinedException(BudgetStatus status) {
        super("Orçamento diário de IA atingido (" + status.callsToday() + "/" + status.dailyLimit()
                + " chamadas); chamada não confirmada.");
    }
}
