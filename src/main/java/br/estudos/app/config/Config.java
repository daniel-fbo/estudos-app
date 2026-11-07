package br.estudos.app.config;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuração do app, gravada em {@code ~/.estudos-app/config.json}.
 *
 * @param models        alias de modelo por tipo de tarefa (chaves: aulas, padrao, barato)
 * @param dailyAiBudget máximo de chamadas de IA por dia
 * @param theme         "escuro" ou "claro"
 */
public record Config(
        String estudosRepo,
        String dbPath,
        String claudePath,
        String agyPath,
        String gccPath,
        String gppPath,
        String codeforcesHandle,
        Map<String, String> models,
        int dailyAiBudget,
        String theme) {

    public static final String MODEL_LESSONS = "aulas";
    public static final String MODEL_DEFAULT = "padrao";
    public static final String MODEL_CHEAP = "barato";
    public static final String THEME_DARK = "escuro";
    public static final String THEME_LIGHT = "claro";

    public Config {
        models = models == null ? Map.of() : Map.copyOf(models);
        theme = theme == null || theme.isBlank() ? THEME_DARK : theme;
    }

    public boolean isDark() {
        return THEME_DARK.equals(theme);
    }

    public Config withTheme(String theme) {
        return new Config(estudosRepo, dbPath, claudePath, agyPath, gccPath, gppPath,
                codeforcesHandle, models, dailyAiBudget, theme);
    }

    public static Config defaults(Path appHome) {
        Map<String, String> models = new LinkedHashMap<>();
        models.put(MODEL_LESSONS, "opus");
        models.put(MODEL_DEFAULT, "sonnet");
        models.put(MODEL_CHEAP, "haiku");
        return new Config(
                Path.of(System.getProperty("user.home"), "estudos").toString(),
                appHome.resolve("estudos.db").toString(),
                "claude", "agy", "gcc", "g++",
                "",
                models,
                30,
                THEME_DARK);
    }

    /** Preenche campos ausentes (config antigo ou editado à mão) com os de {@code d}. */
    public Config withDefaultsFrom(Config d) {
        Map<String, String> m = new LinkedHashMap<>(d.models);
        m.putAll(models);
        return new Config(
                or(estudosRepo, d.estudosRepo), or(dbPath, d.dbPath),
                or(claudePath, d.claudePath), or(agyPath, d.agyPath),
                or(gccPath, d.gccPath), or(gppPath, d.gppPath),
                codeforcesHandle == null ? d.codeforcesHandle : codeforcesHandle,
                m,
                dailyAiBudget > 0 ? dailyAiBudget : d.dailyAiBudget,
                or(theme, d.theme));
    }

    public String model(String task) {
        return models.getOrDefault(task, models.get(MODEL_DEFAULT));
    }

    private static String or(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }
}
