package com.example.slagalica.logic;

public class LeagueLogic {

    public static final String[] LEAGUE_NAMES = {"Početnik", "Bronze", "Silver", "Gold", "Platinum", "Diamond"};
    public static final String[] LEAGUE_ICONS = {"⚪", "🥉", "🥈", "🥇", "💎", "👑"};

    // Broj zvezda potreban za ULAZAK u ligu (indeks = liga)
    private static final int[] LEAGUE_THRESHOLDS = {0, 100, 200, 400, 800, 1600};

    /**
     * Računa koju ligu igrač treba da ima na osnovu broja zvezda.
     */
    public static int calculateLeague(int stars) {
        int league = 0;
        for (int i = LEAGUE_THRESHOLDS.length - 1; i >= 0; i--) {
            if (stars >= LEAGUE_THRESHOLDS[i]) {
                league = i;
                break;
            }
        }
        return league;
    }

    /**
     * Koliko zvezda treba za sledeću ligu.
     * Vraća -1 ako je igrač već u najvišoj ligi.
     */
    public static int starsForNextLeague(int currentLeague) {
        if (currentLeague >= LEAGUE_THRESHOLDS.length - 1) return -1;
        return LEAGUE_THRESHOLDS[currentLeague + 1];
    }

    public static String getLeagueName(int league) {
        int idx = Math.max(0, Math.min(league, LEAGUE_NAMES.length - 1));
        return LEAGUE_NAMES[idx];
    }

    public static String getLeagueIcon(int league) {
        int idx = Math.max(0, Math.min(league, LEAGUE_ICONS.length - 1));
        return LEAGUE_ICONS[idx];
    }

    /**
     * Dodatni tokeni dnevno na osnovu lige (1 token po ligi iznad 0).
     */
    public static int getDailyBonusTokens(int league) {
        return league;
    }
}