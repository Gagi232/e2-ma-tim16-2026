package com.example.slagalica.data.model;

public class User {
    private String id;
    private String username;
    private String email;
    private int tokens;
    private int stars;
    private int league;
    private String region;
    private String avatarUrl;
    private long createdAt;

    public User() {}

    public User(String id, String username, String email, int tokens,
                int stars, int league, String region, String avatarUrl) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.tokens = tokens;
        this.stars = stars;
        this.league = league;
        this.region = region;
        this.avatarUrl = avatarUrl;
        this.createdAt = System.currentTimeMillis();
    }

    // Getteri i setteri
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public int getTokens() { return tokens; }
    public void setTokens(int tokens) { this.tokens = tokens; }

    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }

    public int getLeague() { return league; }
    public void setLeague(int league) { this.league = league; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}