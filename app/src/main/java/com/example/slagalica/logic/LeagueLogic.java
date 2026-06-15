// logic/LeagueLogic.java
package com.example.slagalica.logic;

public class LeagueLogic {

    // Pragovi za ulazak u ligu (indeks = broj lige)
    private static final int[] THRESHOLDS = {0, 100, 200, 400, 800, 1600};
    private static final String[] NAMES    = {"Liga 0",
            "Početnička Liga",
            "Školska Liga",
            "Akademska Liga",
            "Genijalac Liga",
            "Šampionska Liga" };
    private static final String[] ICONS    = {"🏆",
            "📚",
            "🧠",
            "🏛️",
            "👑",
            "🌟"};

    /** Vraća broj lige (0–5) na osnovu ukupnog broja zvezda. */
    public static int calculateLeague(int stars) {
        int league = 0;
        for (int i = THRESHOLDS.length - 1; i >= 0; i--) {
            if (stars >= THRESHOLDS[i]) {
                league = i;
                break;
            }
        }
        return league;
    }

    public static String getLeagueName(int league) {
        if (league < 0 || league >= NAMES.length) return NAMES[0];
        return NAMES[league];
    }

    public static String getLeagueIcon(int league) {
        if (league < 0 || league >= ICONS.length) return ICONS[0];
        return ICONS[league];
    }

    /** Koliko dodatnih tokena dnevno dobija igrač zbog lige. */
    public static int getBonusTokensPerDay(int league) {
        return league; // Liga 0 → 0 bonus, Liga 3 → 3 bonus, itd.
    }
}