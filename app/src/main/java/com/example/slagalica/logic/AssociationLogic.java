package com.example.slagalica.logic;

public class AssociationLogic {

    public static final int ROUNDS = 2;
    public static final long ROUND_TIME_MS = 120_000;

    public static final int FINAL_BASE_PTS   = 7;
    public static final int FINAL_PER_COL    = 6;
    public static final int COL_BASE_PTS     = 2;
    public static final int COL_PER_HIDDEN   = 1;
    public static final int FIELDS_PER_COL   = 4;

    /**
     * Bodovi za otkrivenu kolonu na osnovu broja NEOTVORENIH polja.
     * 2 + 1 za svako neotvoreno polje.
     */
    public static int colScore(int hiddenFields) {
        return COL_BASE_PTS + (hiddenFields * COL_PER_HIDDEN);
    }

    /**
     * Bodovi za konačno rešenje.
     * 7 + 6*broj nepogodjenih kolona + zbir bodova pogodjenih kolona
     */
    public static int finalScore(boolean[] colSolved, int[] hiddenFieldsPerCol) {
        int score = FINAL_BASE_PTS;
        int unsolvedCols = 0;

        for (int i = 0; i < colSolved.length; i++) {
            if (colSolved[i]) {
                score += colScore(hiddenFieldsPerCol[i]);
            } else {
                unsolvedCols++;
            }
        }
        score += unsolvedCols * FINAL_PER_COL;
        return score;
    }
}