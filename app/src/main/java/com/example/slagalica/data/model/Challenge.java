package com.example.slagalica.data.model;

import java.util.HashMap;
import java.util.Map;

public class Challenge {
    private String id;
    private String creatorId;
    private String creatorUsername;
    private String region;
    private int starsWager;
    private int tokensWager;
    private String status; // "open", "full", "finished"
    private long createdAt;
    private Map<String, ParticipantResult> participants = new HashMap<>();

    public Challenge() {}

    public static class ParticipantResult {
        public String username;
        public int score;
        public boolean finished;
        public ParticipantResult() {}
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCreatorId() { return creatorId; }
    public void setCreatorId(String creatorId) { this.creatorId = creatorId; }
    public String getCreatorUsername() { return creatorUsername; }
    public void setCreatorUsername(String creatorUsername) { this.creatorUsername = creatorUsername; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public int getStarsWager() { return starsWager; }
    public void setStarsWager(int starsWager) { this.starsWager = starsWager; }
    public int getTokensWager() { return tokensWager; }
    public void setTokensWager(int tokensWager) { this.tokensWager = tokensWager; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public Map<String, ParticipantResult> getParticipants() { return participants; }
    public void setParticipants(Map<String, ParticipantResult> participants) { this.participants = participants; }
}