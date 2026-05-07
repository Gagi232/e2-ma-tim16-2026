package com.example.slagalica.data.model;

import java.util.HashMap;
import java.util.Map;

public class Match {
    private String id;
    private String player1Id;
    private String player2Id;
    private String status;
    private int currentGame;
    private Map<String, Integer> scores;
    private boolean isFriendly;
    private long createdAt;

    public Match() {
        this.scores = new HashMap<>();
        this.status = "waiting";
        this.createdAt = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPlayer1Id() { return player1Id; }
    public void setPlayer1Id(String player1Id) { this.player1Id = player1Id; }

    public String getPlayer2Id() { return player2Id; }
    public void setPlayer2Id(String player2Id) { this.player2Id = player2Id; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getCurrentGame() { return currentGame; }
    public void setCurrentGame(int currentGame) { this.currentGame = currentGame; }

    public Map<String, Integer> getScores() { return scores; }
    public void setScores(Map<String, Integer> scores) { this.scores = scores; }

    public boolean isFriendly() { return isFriendly; }
    public void setFriendly(boolean friendly) { isFriendly = friendly; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}