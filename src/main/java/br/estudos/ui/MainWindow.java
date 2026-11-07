package br.estudos.ui;

import br.estudos.Services;
import br.estudos.ui.settings.SettingsView;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Janela principal: barra lateral de navegação + área de conteúdo. */
public class MainWindow {

    private final BorderPane root = new BorderPane();

    public MainWindow(Services services) {
        Map<String, Supplier<Node>> screens = new LinkedHashMap<>();
        for (String name : new String[]{"Painel", "Metas", "Código", "Aulas", "Biblioteca",
                "Anki", "Codeforces", "Agenda", "Uso de IA"}) {
            screens.put(name, () -> comingSoon(name));
        }
        screens.put("Configurações", () -> new SettingsView(services).root());

        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");
        ToggleGroup group = new ToggleGroup();
        ToggleButton first = null;
        for (var e : screens.entrySet()) {
            ToggleButton b = new ToggleButton(e.getKey());
            b.getStyleClass().add("nav-button");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setToggleGroup(group);
            b.setOnAction(ev -> {
                if (!b.isSelected()) b.setSelected(true); // não deixa desmarcar
                root.setCenter(e.getValue().get());
            });
            sidebar.getChildren().add(b);
            if (first == null) first = b;
        }
        root.setLeft(sidebar);
        first.setSelected(true);
        root.setCenter(screens.get(first.getText()).get());
    }

    public Parent root() {
        return root;
    }

    private static Node comingSoon(String name) {
        Label title = new Label(name);
        title.getStyleClass().add("screen-title");
        VBox box = new VBox(12, title, new Label("Em breve"));
        box.setAlignment(Pos.CENTER);
        return new StackPane(box);
    }
}
