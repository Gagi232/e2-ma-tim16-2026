package com.example.slagalica.logic;

public class KoZnaZnaLogic {

    public static final int TOTAL_QUESTIONS = 5;
    public static final int PTS_CORRECT     = 10;
    public static final int PTS_WRONG       = -5;
    public static final long QUESTION_TIME_MS = 5_000;

    /**
     * Solo mod: jedan igrac odgovori.
     * Vraca promenu bodova (+10 tacno, -5 netacno, 0 timeout).
     */
    public int calcSoloScore(int selectedIndex, int correctIndex) {
        if (selectedIndex < 0) return 0;               // timeout
        return (selectedIndex == correctIndex) ? PTS_CORRECT : PTS_WRONG;
    }

    /**
     * Multiplayer mod: oba igraca su odgovorili.
     * Vraca int[2] = { bodovi za mene, bodovi za protivnika }.
     *
     * Pravila (spec):
     *  - Oba tacno  -> brzi dobija +10
     *  - Samo jedan tacno -> on dobija +10
     *  - Oba netacno i nisu timeout (-1) -> oba -5
     *  - Timeout (answerIndex == -1) se tretira kao da nije odgovoreno
     */
    public int[] calcMultiScore(
            int myIndex,   boolean myCorrect,   long myTimestamp,
            int oppIndex,  boolean oppCorrect,  long oppTimestamp) {

        int myPts  = 0;
        int oppPts = 0;

        if (myCorrect && oppCorrect) {
            if (myTimestamp <= oppTimestamp) myPts  = PTS_CORRECT;
            else                             oppPts = PTS_CORRECT;
        } else if (myCorrect) {
            myPts = PTS_CORRECT;
        } else if (oppCorrect) {
            oppPts = PTS_CORRECT;
        } else {
            if (myIndex  >= 0) myPts  = PTS_WRONG;
            if (oppIndex >= 0) oppPts = PTS_WRONG;
        }

        return new int[]{myPts, oppPts};
    }

    /** Vraca true ako je igrač odgovorio pre isteka vremena. */
    public boolean answeredInTime(long timestamp) {
        return timestamp > 0;
    }
}