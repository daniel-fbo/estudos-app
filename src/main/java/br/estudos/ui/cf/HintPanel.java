package br.estudos.ui.cf;

import br.estudos.Services;
import br.estudos.ui.ai.FxBudgetGate;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Dicas em 3 níveis para um problema; cada nível só abre depois do anterior. */
public class HintPanel {
    private final Services services;
    private final VBox root = new VBox(8);
    private final TextField ref = new TextField();
    private final TextArea statement = new TextArea();
    private final TextArea output = new TextArea();
    private final Label status = new Label();
    private final Button[] buttons = new Button[3];

    public HintPanel(Services services, String initialRef) {
        this.services = services;
        ref.setPromptText("Problema (ex.: 1900A ou nome do arquivo)");
        ref.setText(initialRef == null ? "" : initialRef);
        statement.setPromptText("Cole o enunciado ou a URL do problema");
        statement.setPrefRowCount(6);
        statement.setWrapText(true);
        output.setEditable(false);
        output.setWrapText(true);
        output.setPrefRowCount(8);
        String[] names = {"Dica 1 (tema)", "Dica 2 (observação-chave)", "Dica 3 (esboço)"};
        HBox bar = new HBox(8);
        for (int i = 0; i < 3; i++) {
            int level = i + 1;
            buttons[i] = new Button(names[i]);
            buttons[i].setOnAction(e -> request(level));
            bar.getChildren().add(buttons[i]);
        }
        ref.textProperty().addListener((o, a, b) -> updateButtons());
        root.getChildren().addAll(new Label("Dicas (sem código)"), ref, statement, bar, status, output);
        updateButtons();
    }

    public Node root() { return root; }

    private void updateButtons() {
        String r = ref.getText();
        services.background().submit(() -> {
            int max = services.cf().maxHintLevel(r);
            Platform.runLater(() -> {
                for (int i = 0; i < 3; i++) buttons[i].setDisable(r.isBlank() || i + 1 > max + 1);
            });
        });
    }

    private void request(int level) {
        String r = ref.getText(), st = statement.getText();
        status.setText("Pedindo a dica " + level + "…");
        services.background().submit(() -> {
            String text = null, err = null;
            try {
                text = services.cf().hint(r, level, st, new FxBudgetGate());
            } catch (Throwable e) {
                Throwable c = e.getCause() != null && e instanceof java.util.concurrent.ExecutionException ? e.getCause() : e;
                err = "Erro: " + c.getMessage();
            }
            String t = text, er = err;
            Platform.runLater(() -> {
                status.setText(er == null ? "" : er);
                if (t != null) output.setText("Dica " + level + ":\n" + t);
                updateButtons();
            });
        });
    }
}
