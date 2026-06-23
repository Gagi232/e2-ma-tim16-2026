package com.example.slagalica.data.model;

public class DailyMission {
    private String id;
    private String userId;
    private boolean winMatch;
    private boolean sendMessage;
    private boolean friendlyMatch;
    private boolean winTournament;
    private long date;

    public DailyMission() {}

    private boolean bonusClaimed;

    public boolean isBonusClaimed() { return bonusClaimed; }
    public void setBonusClaimed(boolean bonusClaimed) { this.bonusClaimed = bonusClaimed; }

    public boolean allComplete() {
        return winMatch && sendMessage && friendlyMatch && winTournament;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public boolean isWinMatch() { return winMatch; }
    public void setWinMatch(boolean winMatch) { this.winMatch = winMatch; }

    public boolean isSendMessage() { return sendMessage; }
    public void setSendMessage(boolean sendMessage) { this.sendMessage = sendMessage; }

    public boolean isFriendlyMatch() { return friendlyMatch; }
    public void setFriendlyMatch(boolean friendlyMatch) { this.friendlyMatch = friendlyMatch; }

    public boolean isWinTournament() { return winTournament; }
    public void setWinTournament(boolean winTournament) { this.winTournament = winTournament; }

    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }
}