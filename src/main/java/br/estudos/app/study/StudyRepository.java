package br.estudos.app.study;

import br.estudos.domain.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Porta de persistência de sessões e metas. */
public interface StudyRepository {
    StudySession saveSession(StudySession s);
    /** Atualiza fim, duração e nota de uma sessão existente. */
    void updateSession(StudySession s);
    void deleteSession(long id);
    /** Sessão aberta mais recente ({@code ended_at} nulo), se houver. */
    Optional<StudySession> openSession();
    List<StudySession> sessionsSince(Instant from);
    List<StudySession> lastSessions(int n);
    /** Instantes de início de todas as sessões encerradas. */
    List<Instant> allSessionStarts();

    Goal saveGoal(Goal g);
    void deleteGoal(long id);
    List<Goal> goals(boolean onlyActive);

    /** Contador manual: registra um delta. */
    void logCountDelta(long goalId, int delta, Instant at);
    List<Dated> countDeltas(long goalId);

    /** Instante do primeiro AC de cada arquivo-fonte em {@code code_run}. */
    List<Instant> firstAcTimes();

    /** Instante do primeiro OK de cada problema (contest_id+problem_index) em {@code cf_submission}. */
    List<Instant> cfFirstAcTimes();

    /** Avanços de páginas (positivos) dos livros da área. */
    List<Dated> pagesRead(Area area);
}
