package com.example.slagalica.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.slagalica.MainActivity;
import com.example.slagalica.R;
import com.example.slagalica.data.repository.StatsRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.google.android.material.button.MaterialButton;

public class ResultsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_results);

        // Uzmi podatke iz intenta
        boolean isGuest   = getIntent().getBooleanExtra("isGuest", false);
        int myTotal       = getIntent().getIntExtra("totalMyScore", 0);
        int oppTotal      = getIntent().getIntExtra("totalOpponentScore", 0);
        String myId       = getIntent().getStringExtra("myId");

        boolean iWon = myTotal > oppTotal;

        // Prikaži rezultate
        TextView tvResult      = findViewById(R.id.tvResult);
        TextView tvP1Score     = findViewById(R.id.tvPlayer1Score);
        TextView tvP2Score     = findViewById(R.id.tvPlayer2Score);
        TextView tvStarsEarned = findViewById(R.id.tvStarsEarned);

        tvResult.setText(iWon ? "🎉 Pobedio si!" : "😔 Izgubio si!");
        tvP1Score.setText(String.valueOf(myTotal));
        tvP2Score.setText(String.valueOf(oppTotal));

        // Izračunaj zvezde po specifikaciji
        int starsChange = calculateStars(iWon, myTotal);
        if (iWon) {
            tvStarsEarned.setText("+" + starsChange + " ⭐");
        } else {
            tvStarsEarned.setText(starsChange + " ⭐"); // već je negativan ili 0
        }

        // Upiši statistiku i ažuriraj zvezde (samo za registrovane, ne gost)
        if (!isGuest && myId != null) {
            saveResults(iWon, myTotal, starsChange);
        }

        // Dugme za povratak
        ((MaterialButton) findViewById(R.id.btnHome)).setOnClickListener(v -> {
            Intent intent = isGuest
                    ? new Intent(this, GuestActivity.class)
                    : new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }

    /**
     * Spec: Pobednik dobija 10 zvezdi + 1 zvezda za svakih 40 bodova.
     * Gubitnik gubi 10 zvezdi, ali dobija 1 zvezdu za svakih 40 bodova.
     * Vraća finalni broj zvezda koji se dodaje (pozitivan za pobednika,
     * može biti negativan za gubitnika).
     */
    private int calculateStars(boolean won, int score) {
        int bonusStars = score / 40; // 1 zvezda za svakih 40 bodova
        if (won) {
            return 10 + bonusStars;  // +10 + bonus
        } else {
            return bonusStars - 10;  // -10 + bonus (može biti negativno)
        }
    }

    private void saveResults(boolean won, int myScore, int starsChange) {
        StatsRepository statsRepo = new StatsRepository();
        UserRepository  userRepo  = new UserRepository();

        // 1. Upiši match rezultat u stats kolekciju
        statsRepo.saveMatchResult(won, new StatsRepository.Callback<Void>() {
            @Override public void onSuccess(Void r) {}
            @Override public void onError(Exception e) {}
        });

        // 2. Ažuriraj zvezde na User dokumentu
        userRepo.getCurrentUser(new UserRepository.Callback<com.example.slagalica.data.model.User>() {
            @Override
            public void onSuccess(com.example.slagalica.data.model.User user) {
                int currentStars = user.getStars();
                int newStars = Math.max(0, currentStars + starsChange); // ne može ispod 0

                // Proveri da li 50 zvezda = 1 token
                int currentTokens = user.getTokens();
                int tokensFromStars = newStars / 50 - currentStars / 50;
                // Svaki prelaz preko multiple od 50 daje 1 token
                // Samo ako je starsChange pozitivan (pobeda ili bonus)
                int tokenBonus = Math.max(0, tokensFromStars);

                userRepo.updateField("stars", newStars,
                        new UserRepository.Callback<Void>() {
                            @Override public void onSuccess(Void r) {}
                            @Override public void onError(Exception e) {}
                        });

                if (tokenBonus > 0) {
                    int newTokens = currentTokens + tokenBonus;
                    userRepo.updateField("tokens", newTokens,
                            new UserRepository.Callback<Void>() {
                                @Override public void onSuccess(Void r) {}
                                @Override public void onError(Exception e) {}
                            });
                }

                // 3. Ažuriraj ligu na osnovu novih zvezda
                int newLeague = com.example.slagalica.logic.LeagueLogic.calculateLeague(newStars);
                if (newLeague != user.getLeague()) {
                    userRepo.updateField("league", newLeague,
                            new UserRepository.Callback<Void>() {
                                @Override public void onSuccess(Void r) {}
                                @Override public void onError(Exception e) {}
                            });
                }
            }
            @Override
            public void onError(Exception e) {}
        });
    }
}