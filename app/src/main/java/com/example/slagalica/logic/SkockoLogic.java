package com.example.slagalica.logic;

import java.util.List;

public class SkockoLogic {

    public static final int MAX_ATTEMPTS = 6;
    public static final long ROUND_TIME_MS = 30_000;
    public static final long OPPONENT_TRY_MS = 10_000;

    public static final int PTS_ATTEMPT_1_2 = 20;
    public static final int PTS_ATTEMPT_3_4 = 15;
    public static final int PTS_ATTEMPT_5_6 = 10;
    public static final int PTS_OPPONENT    = 10;

    /** Vraca true ako se pogodjeni niz poklapa sa tacnim. */
    public static boolean isCorrect(List<String> guess, List<String> solution) {
        if (guess.size() != solution.size()) return false;
        for (int i = 0; i < guess.size(); i++) {
            if (!guess.get(i).equals(solution.get(i))) return false;
        }
        return true;
    }

    /**
     * Vraca broj poena na osnovu rednog broja pokušaja (1-6) u kom je pogodjeno.
     */
    public static int pointsForAttempt(int attemptNumber) {
        if (attemptNumber <= 2) return PTS_ATTEMPT_1_2;
        if (attemptNumber <= 4) return PTS_ATTEMPT_3_4;
        if (attemptNumber <= 6) return PTS_ATTEMPT_5_6;
        return 0;
    }

    /** Koliko pozicija se poklapa (feedback za igrača) */
    public static int countMatches(List<String> guess, List<String> solution) {
        int matches = 0;
        for (int i = 0; i < guess.size() && i < solution.size(); i++) {
            if (guess.get(i).equals(solution.get(i))) matches++;
        }
        return matches;
    }
}