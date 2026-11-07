package br.estudos.infra.config;

import java.nio.file.Path;

/** Pastas do app. A pasta base pode ser trocada com {@code -Destudos.home=...}. */
public final class AppPaths {

    public static final String HOME_PROPERTY = "estudos.home";

    private AppPaths() {}

    public static Path home() {
        String p = System.getProperty(HOME_PROPERTY);
        return p != null && !p.isBlank()
                ? Path.of(p)
                : Path.of(System.getProperty("user.home"), ".estudos-app");
    }

    /** Expande {@code ~} no início do caminho. */
    public static Path expand(String path) {
        if (path.equals("~")) return Path.of(System.getProperty("user.home"));
        if (path.startsWith("~/")) return Path.of(System.getProperty("user.home"), path.substring(2));
        return Path.of(path);
    }
}
