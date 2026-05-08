package com.example.slagalica.util;

import java.util.Arrays;
import java.util.List;

public class Constants {

    // Firestore kolekcije
    public static final String COLLECTION_USERS = "users";
    public static final String COLLECTION_MATCHES = "matches";
    public static final String COLLECTION_LEADERBOARD_WEEKLY = "leaderboard_weekly";
    public static final String COLLECTION_LEADERBOARD_MONTHLY = "leaderboard_monthly";
    public static final String COLLECTION_CHAT = "chat";
    public static final String COLLECTION_MESSAGES = "messages";
    public static final String COLLECTION_CHALLENGES = "challenges";
    public static final String COLLECTION_TOURNAMENTS = "tournaments";
    public static final String COLLECTION_NOTIFICATIONS = "notifications";
    public static final String COLLECTION_MISSIONS = "daily_missions";

    // Notification kanali
    public static final String CHANNEL_CHAT = "channel_chat";
    public static final String CHANNEL_RANKING = "channel_ranking";
    public static final String CHANNEL_REWARDS = "channel_rewards";
    public static final String CHANNEL_OTHER = "channel_other";

    // Indeksi igara
    public static final int GAME_KO_ZNA_ZNA = 0;
    public static final int GAME_SPOJNICE = 1;
    public static final int GAME_ASOCIJACIJE = 2;
    public static final int GAME_SKOCKO = 3;
    public static final int GAME_KORAK_PO_KORAK = 4;
    public static final int GAME_MOJ_BROJ = 5;

    // Lige — broj zvezda potrebnih za svaku
    public static final int LEAGUE_1_STARS = 100;
    public static final int LEAGUE_2_STARS = 200;
    public static final int LEAGUE_3_STARS = 400;
    public static final int LEAGUE_4_STARS = 800;
    public static final int LEAGUE_5_STARS = 1600;

    // Tokeni
    public static final int INITIAL_TOKENS = 5;
    public static final int DAILY_TOKENS = 5;

    // Regioni Srbije
    public static final List<String> REGIONS = Arrays.asList(
            "Vojvodina",
            "Beograd",
            "Šumadija",
            "Zapadna Srbija",
            "Istočna Srbija",
            "Južna Srbija",
            "Kosovo i Metohija"
    );
}
