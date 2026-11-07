package br.estudos.app.ai;

/** Pergunta ao usuário se pode passar do orçamento. Chamado fora da thread da UI; pode bloquear. */
@FunctionalInterface
public interface BudgetGate {
    boolean confirm(BudgetStatus status);

    BudgetGate ALWAYS = s -> true;
    BudgetGate NEVER = s -> false;
}
