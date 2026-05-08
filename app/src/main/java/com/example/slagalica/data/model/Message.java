package com.example.slagalica.data.model;

public class Message {
    private String id;
    private String senderId;
    private String senderUsername;
    private String regionId;
    private String text;
    private long createdAt;

    public Message() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getSenderUsername() { return senderUsername; }
    public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }

    public String getRegionId() { return regionId; }
    public void setRegionId(String regionId) { this.regionId = regionId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}