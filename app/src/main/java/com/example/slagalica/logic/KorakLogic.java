package com.example.slagalica.logic;

public class KorakLogic {
    public static final long ROUND_TIME_MS = 70_000;
    public static final long BONUS_TIME_MS = 10_000;
    public static final int BONUS_SCORE = 5;
    public static final int STEP_INTERVAL_MS = 10_000;
    public static final int TOTAL_STEPS = 7;

    /** Bodovi za pogodak na koraku N (1-indeksirano): korak1=20, korak2=18, ... korak7=8 */
    public static int scoreForStep(int openedSteps) {
        return Math.max(0, 20 - 2 * (openedSteps - 1));
    }

    /** Koliko je koraka trenutno otvoreno na osnovu protekog vremena od starta runde. */
    public static int openedStepsFromElapsed(long elapsedMs) {
        int steps = (int) (elapsedMs / STEP_INTERVAL_MS) + 1;
        return Math.min(TOTAL_STEPS, Math.max(1, steps));
    }
}