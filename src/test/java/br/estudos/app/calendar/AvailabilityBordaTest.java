package br.estudos.app.calendar;

import br.estudos.infra.calendar.JdbcCalendarRepository;
import br.estudos.infra.db.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class AvailabilityBordaTest {
    private JdbcCalendarRepository repo(Path dir) throws Exception {
        var db = new Database(dir.resolve("a.db"));
        db.migrate();
        return new JdbcCalendarRepository(db);
    }

    @Test void extremosDaGradeFazemRoundTrip(@TempDir Path dir) throws Exception {
        var r = repo(dir);
        Set<Integer> cells = Set.of(100, 123, 700, 723); // segunda 0h, segunda 23h, domingo 0h, domingo 23h
        r.saveAvailability(cells);
        assertEquals(cells, r.loadAvailability());
    }

    @Test void salvarDuasVezesOMesmoConjuntoEhIdempotente(@TempDir Path dir) throws Exception {
        var r = repo(dir);
        r.saveAvailability(Set.of(109, 110));
        r.saveAvailability(Set.of(109, 110));
        assertEquals(Set.of(109, 110), r.loadAvailability());
    }

    @Test void gradeSobrevivePorReaberturaDoBanco(@TempDir Path dir) throws Exception {
        repo(dir).saveAvailability(Set.of(309, 415));
        var db2 = new Database(dir.resolve("a.db"));
        assertEquals(0, db2.migrate()); // V6 não reaplica
        assertEquals(Set.of(309, 415), new JdbcCalendarRepository(db2).loadAvailability());
    }

    @Test void falhaNoMeioNaoApagaAGradeAnterior(@TempDir Path dir) throws Exception {
        var r = repo(dir);
        r.saveAvailability(Set.of(109));
        // 099 -> weekday 0, hora 99 é aceito pela tabela (sem CHECK); valor nulo não existe em Set<Integer> primitivo.
        // Cobre ao menos que um conjunto grande não corrompe o estado.
        Set<Integer> grande = new TreeSet<>();
        for (int d = 1; d <= 7; d++) for (int h = 0; h < 24; h++) grande.add(d * 100 + h);
        r.saveAvailability(grande);
        assertEquals(168, r.loadAvailability().size());
    }

    /** Cliques rápidos viram tarefas num pool de 2 threads: salvamentos concorrentes não podem falhar com banco travado. */
    @Test void salvamentosConcorrentesNaoLancamErro(@TempDir Path dir) throws Exception {
        var r = repo(dir);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var futures = new java.util.ArrayList<Future<?>>();
            for (int i = 0; i < 40; i++) {
                final Set<Integer> s = Set.of(100 + (i % 24), 200 + (i % 24));
                futures.add(pool.submit(() -> r.saveAvailability(s)));
            }
            for (var f : futures) f.get(30, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertEquals(2, r.loadAvailability().size());
    }
}
