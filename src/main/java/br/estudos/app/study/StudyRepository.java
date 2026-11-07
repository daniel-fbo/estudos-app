package br.estudos.app.study;

import br.estudos.domain.*;

import java.time.Instant;
import java.util.List;

/** Porta de persistência de sessões e metas. */
public interface StudyRepository {
    StudySession saveSession(StudySession s);
    List<StudySession> sessionsSince(Instant from);
    List<StudySession> lastSessions(int n);
    /** Instantes de início de todas as sessões encerradas. */
    List<Instant> allSessionStarts();

    Goal saveGoal(Goal g);
    void deleteGoal(long id);
    List<Goal> goals(boolean onlyActive);
    void setManualCount(long goalId, int count);
}
