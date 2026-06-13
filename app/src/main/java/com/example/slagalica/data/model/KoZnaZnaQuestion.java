package com.example.slagalica.data.model;

import java.util.List;

public class KoZnaZnaQuestion {
    private String id;
    private String question;
    private List<String> options; // 4 opcije
    private int correctIndex;     // 0-3

    public KoZnaZnaQuestion() {}

    public KoZnaZnaQuestion(String question, List<String> options, int correctIndex) {
        this.question = question;
        this.options = options;
        this.correctIndex = correctIndex;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }
    public int getCorrectIndex() { return correctIndex; }
    public void setCorrectIndex(int correctIndex) { this.correctIndex = correctIndex; }
}