package com.example.slagalica.logic;

import java.util.Map;

public class SpojniceLogic {

    public static final int  PAIRS_COUNT   = 5;
    public static final int  PTS_PER_PAIR  = 2;
    public static final long TURN_TIME_MS  = 30_000;

    /**
     * Vraca true ako je levi pojam (leftIndex) ispravno spojen
     * sa desnim (rightPairIndex). Ispravno = isti indeks.
     */
    public boolean isCorrectConnection(int leftIndex, int rightPairIndex) {
        return leftIndex == rightPairIndex;
    }

    /**
     * Racuna bodove za jednog igraca na osnovu njegovih konekcija.
     * connections: leftIndex -> rightPairIndex
     * Svaki ispravno spojen par donosi PTS_PER_PAIR.
     */
    public int calcScore(Map<Integer, Integer> connections) {
        int score = 0;
        for (Map.Entry<Integer, Integer> entry : connections.entrySet()) {
            if (isCorrectConnection(entry.getKey(), entry.getValue())) {
                score += PTS_PER_PAIR;
            }
        }
        return score;
    }

    /**
     * Vraca max moguc broj bodova za rundu (svi parovi tacno).
     */
    public int maxRoundScore() {
        return PAIRS_COUNT * PTS_PER_PAIR;
    }

    /**
     * Da li su svi parovi popunjeni (oba igraca zajedno).
     */
    public boolean allPairsFilled(Map<Integer, Integer> myConns,
                                  Map<Integer, Integer> oppConns) {
        return (myConns.size() + oppConns.size()) >= PAIRS_COUNT;
    }
}