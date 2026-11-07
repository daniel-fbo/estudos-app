package br.estudos.ui.ai;

import br.estudos.app.ai.BudgetGate;
import br.estudos.app.ai.BudgetStatus;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.concurrent.CompletableFuture;

/** Mostra um diálogo de confirmação na thread do JavaFX e espera a resposta (chamado de uma thread da fila). */
public final class FxBudgetGate implements BudgetGate {
    @Override
    public boolean confirm(BudgetStatus s) {
        CompletableFuture<Boolean> answer = new CompletableFuture<>();
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                    "Hoje já foram " + s.callsToday() + " chamadas de IA (orçamento: " + s.dailyLimit()
                            + ", " + s.tokensToday() + " tokens).\nFazer mais esta chamada?",
                    ButtonType.YES, ButtonType.NO);
            a.setHeaderText("Orçamento diário de IA atingido");
            answer.complete(a.showAndWait().orElse(ButtonType.NO) == ButtonType.YES);
        });
        try {
            return answer.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
