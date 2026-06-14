package com.example.slagalica.data.model;

import java.util.List;

public class SpojniceSet {
    private String id;
    private String category; // npr. "Poveži pevača sa pesmom"
    private List<SpojnicePar> pairs; // tačno 5 parova

    public SpojniceSet() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public List<SpojnicePar> getPairs() { return pairs; }
    public void setPairs(List<SpojnicePar> pairs) { this.pairs = pairs; }
}