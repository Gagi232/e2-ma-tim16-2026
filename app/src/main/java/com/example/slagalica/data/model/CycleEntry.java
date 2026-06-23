package com.example.slagalica.data.model;

public class CycleEntry {
    private String userId;
    private String username;
    private int stars;
    private int league;
    private String cycleKey;

    public CycleEntry() {}

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }
    public int getLeague() { return league; }
    public void setLeague(int league) { this.league = league; }
    public String getCycleKey() { return cycleKey; }
    public void setCycleKey(String cycleKey) { this.cycleKey = cycleKey; }
}