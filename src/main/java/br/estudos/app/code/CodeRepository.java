package br.estudos.app.code;

import br.estudos.domain.TestCase;

import java.util.List;

/** Porta de persistência de casos de teste e execuções. */
public interface CodeRepository {
    TestCase saveCase(String sourcePath, String input, String expected, String origin);
    void deleteCase(long id);
    List<TestCase> cases(String sourcePath);
    void saveRun(String sourcePath, String verdict, int passed, int total, String detail);
    /** Soma 1 em auto_count_ac de cada meta ativa de CONTAGEM e área CP. */
    void creditAcToCpGoals();
}
