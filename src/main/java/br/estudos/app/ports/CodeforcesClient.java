package br.estudos.app.ports;

import br.estudos.app.cf.CfException;
import br.estudos.app.cf.CfRatingPoint;
import br.estudos.app.cf.CfSubmission;

import java.util.List;

/** API pública do Codeforces. A implementação respeita 1 requisição a cada 2 s. */
public interface CodeforcesClient {
    List<CfSubmission> userStatus(String handle) throws CfException;

    List<CfRatingPoint> userRating(String handle) throws CfException;
}
