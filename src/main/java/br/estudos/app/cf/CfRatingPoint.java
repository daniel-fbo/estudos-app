package br.estudos.app.cf;

public record CfRatingPoint(int contestId, String contestName, String ratedAt, int oldRating, int newRating) {}
