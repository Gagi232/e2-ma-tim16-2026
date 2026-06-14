package com.example.slagalica.data.model;

import java.util.List;

public class SkockoCombo {
    private String id;
    private List<String> combination; // 4 simbola, npr ["⬛","🔴","⭐","💛"]

    public SkockoCombo() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public List<String> getCombination() { return combination; }
    public void setCombination(List<String> combination) { this.combination = combination; }
}