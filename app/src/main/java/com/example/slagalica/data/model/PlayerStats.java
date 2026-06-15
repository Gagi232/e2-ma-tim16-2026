package com.example.slagalica.data.model;

public class PlayerStats {
    // Opšte
    public long totalGames;
    public long wins;

    // Ko zna zna
    public long kzzCorrect;      // tačni odgovori ukupno
    public long kzzWrong;        // netačni odgovori ukupno
    public long kzzTotalScore;   // za računanje proseka
    public long kzzRounds;       // broj odigranih rundi

    // Spojnice
    public long spojniceCorrect; // uspešno povezani pojmovi ukupno
    public long spojniceTotal;   // ukupno pojmova (uvek rounds*5)
    public long spojniceTotalScore;
    public long spojniceRounds;

    // Asocijacije
    public long asocijacijeSolved;   // rešene asocijacije
    public long asocijacijeTotal;    // ukupno asocijacija
    public long asocijacijeTotalScore;
    public long asocijacijeRounds;

    // Skočko
    public long skockoSolved;        // pogođene kombinacije
    public long skockoTotal;         // ukupno pokušaja
    public long skockoTotalScore;
    public long skockoRounds;

    // Korak po korak
    public long korakSolved;
    public long korakTotal;
    public long korakTotalScore;
    public long korakRounds;

    // Moj broj
    public long mojBrojExact;    // tačno pogođen broj
    public long mojBrojTotal;
    public long mojBrojTotalScore;
    public long mojBrojRounds;

    public PlayerStats() {}
}