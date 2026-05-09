package com.example.slagalica.data.model;

public class Korak {
    private int redniBroj;
    private String hint;
    private KorakState state;

    public Korak() {
    }

    public Korak(int redniBroj, String hint, KorakState state) {
        this.redniBroj = redniBroj;
        this.hint = hint;
        this.state = state;
    }

    public int getRedniBroj() {
        return redniBroj;
    }

    public void setRedniBroj(int redniBroj) {
        this.redniBroj = redniBroj;
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    public KorakState getState() {
        return state;
    }

    public void setState(KorakState state) {
        this.state = state;
    }
}

