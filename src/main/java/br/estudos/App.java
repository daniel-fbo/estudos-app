package br.estudos;

import br.estudos.app.config.Config;
import br.estudos.infra.config.AppPaths;
import br.estudos.ui.MainWindow;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App extends Application {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    private Services services;

    @Override
    public void init() throws Exception {
        Thread.setDefaultUncaughtExceptionHandler(
                (t, e) -> log.error("Exceção não tratada na thread {}", t.getName(), e));
        services = Services.create(AppPaths.home());
    }

    @Override
    public void start(Stage stage) {
        MainWindow mainWindow = new MainWindow(services);
        Scene scene = new Scene(mainWindow.root(), 1100, 720);
        scene.getStylesheets().add(App.class.getResource("/app.css").toExternalForm());

        // Aplica tema
        applyTheme(scene, services.config());

        // Ícone
        try {
            var iconUrl = App.class.getResource("/icon.png");
            if (iconUrl != null) {
                stage.getIcons().add(new Image(iconUrl.toExternalForm()));
            }
        } catch (Exception e) {
            log.warn("Falha ao carregar ícone", e);
        }

        // Atalhos de teclado globais
        scene.setOnKeyPressed(e -> {
            if (e.isControlDown() || e.isMetaDown()) {
                switch (e.getCode()) {
                    case DIGIT1 -> mainWindow.selectScreen(0);
                    case DIGIT2 -> mainWindow.selectScreen(1);
                    case DIGIT3 -> mainWindow.selectScreen(2);
                    case DIGIT4 -> mainWindow.selectScreen(3);
                    case DIGIT5 -> mainWindow.selectScreen(4);
                    case DIGIT6 -> mainWindow.selectScreen(5);
                    case DIGIT7 -> mainWindow.selectScreen(6);
                    case DIGIT8 -> mainWindow.selectScreen(7);
                    case DIGIT9 -> mainWindow.selectScreen(8);
                    case DIGIT0 -> mainWindow.selectScreen(9);
                    default -> { /* nada */ }
                }
            }
        });

        stage.setTitle("Estudos");
        stage.setScene(scene);
        stage.show();
        log.info("Janela principal aberta");
    }

    /** Aplica a classe de tema na raiz da cena. */
    public static void applyTheme(Scene scene, Config config) {
        scene.getRoot().getStyleClass().removeAll("theme-escuro", "theme-claro");
        scene.getRoot().getStyleClass().add("theme-" + config.theme());
    }

    @Override
    public void stop() {
        if (services != null) services.close();
        log.info("App encerrado");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
