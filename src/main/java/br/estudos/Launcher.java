package br.estudos;

import javafx.application.Application;

/**
 * Ponto de entrada do jar e do pacote jpackage. Precisa ser uma classe que NÃO estende
 * {@link Application}: com o JavaFX no classpath, o launcher do Java recusa uma classe
 * principal que estende Application ("JavaFX runtime components are missing").
 */
public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        Application.launch(App.class, args);
    }
}
