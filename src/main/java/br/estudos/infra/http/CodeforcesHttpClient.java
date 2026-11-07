package br.estudos.infra.http;

import br.estudos.app.cf.*;
import br.estudos.app.ports.CodeforcesClient;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/** Cliente da API pública do Codeforces, com intervalo mínimo de 2 s entre requisições. */
public class CodeforcesHttpClient implements CodeforcesClient {
    public static final String DEFAULT_URL = "https://codeforces.com/api";
    public static final long MIN_INTERVAL_MS = 2000;

    private final String base;
    private final long intervalMs;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private long last = 0; // System.nanoTime() da última requisição; 0 = nenhuma
    private boolean any = false;

    public CodeforcesHttpClient(String base, long intervalMs) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.intervalMs = intervalMs;
    }

    @Override
    public List<CfSubmission> userStatus(String handle) throws CfException {
        return CfStats.parseStatus(get("/user.status?handle=" + enc(handle)));
    }

    @Override
    public List<CfRatingPoint> userRating(String handle) throws CfException {
        return CfStats.parseRating(get("/user.rating?handle=" + enc(handle)));
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private synchronized String get(String path) throws CfException {
        try {
            if (any) {
                long wait = intervalMs - (System.nanoTime() - last) / 1_000_000;
                if (wait > 0) Thread.sleep(wait);
            }
            any = true;
            last = System.nanoTime();
            HttpRequest req = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(60)).GET().build();
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // O Codeforces devolve JSON com status FAILED e comentário também em HTTP 400.
            if (r.statusCode() != 200 && r.statusCode() != 400)
                throw new CfException("Codeforces respondeu HTTP " + r.statusCode());
            return r.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CfException("Sincronização interrompida.", e);
        } catch (IOException e) {
            throw new CfException("Não foi possível falar com o Codeforces: " + e.getMessage(), e);
        }
    }
}
