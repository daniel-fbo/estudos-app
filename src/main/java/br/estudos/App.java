package br.estudos;

import br.estudos.infra.config.AppPaths;
import br.estudos.ui.MainWindow;
import javafx.application.Application;
import javafx.scene.Scene;
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
        Scene scene = new Scene(new MainWindow(services).root(), 1100, 720);
        scene.getStylesheets().add(App.class.getResource("/app.css").toExternalForm());
        stage.setTitle("Estudos");
        stage.setScene(scene);
        stage.show();
        log.info("Janela principal aberta");
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
