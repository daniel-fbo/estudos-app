package br.estudos.ui.ai;

import br.estudos.app.ai.BudgetGate;
import br.estudos.app.ai.BudgetStatus;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.concurrent.CompletableFuture;

/** Mostra um diálogo de confirmação na thread do JavaFX e espera a resposta (chamado de uma thread da fila). */
public final class FxBudgetGate implements BudgetGate {
    private static boolean ask(BudgetStatus s) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                "Estourou o " + s.reason() + ".\nHoje: " + s.callsToday() + " chamadas, "
                        + s.tokensToday() + " tokens.\nFazer mais esta chamada?",
                ButtonType.YES, ButtonType.NO);
        a.setHeaderText("Orçamento diário de IA atingido");
        return a.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    @Override
    public boolean confirm(BudgetStatus s) {
        if (Platform.isFxApplicationThread()) return ask(s);
        CompletableFuture<Boolean> answer = new CompletableFuture<>();
        Platform.runLater(() -> answer.complete(ask(s)));
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
