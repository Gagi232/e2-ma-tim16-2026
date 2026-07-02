package com.example.slagalica.ui.main;
import com.example.slagalica.data.model.AppNotification;
import com.example.slagalica.logic.AppNotificationManager;
import com.google.firebase.auth.FirebaseAuth;
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
        boolean isFriendly = getIntent().getBooleanExtra("isFriendly", false); // NOVO
        int myTotal       = getIntent().getIntExtra("totalMyScore", 0);
        int oppTotal      = getIntent().getIntExtra("totalOpponentScore", 0);
        boolean isLoggedIn = FirebaseAuth.getInstance().getCurrentUser() != null;
        boolean iWon = myTotal > oppTotal;

        // Prikaži rezultate
        TextView tvResult      = findViewById(R.id.tvResult);
        TextView tvP1Score     = findViewById(R.id.tvPlayer1Score);
        TextView tvP2Score     = findViewById(R.id.tvPlayer2Score);
        TextView tvStarsEarned = findViewById(R.id.tvStarsEarned);

        tvResult.setText(iWon ? "🎉 Pobedio si!" : "😔 Izgubio si!");
        tvP1Score.setText(String.valueOf(myTotal));
        tvP2Score.setText(String.valueOf(oppTotal));

        boolean isTournament = getIntent().getBooleanExtra("isTournament", false);
        String tournamentId  = getIntent().getStringExtra("tournamentId");
        String matchKey      = getIntent().getStringExtra("tournamentMatchKey");
        boolean isFinal      = getIntent().getBooleanExtra("isTournamentFinal", false);

        if (isTournament && isLoggedIn) {
            handleTournamentResult(iWon, myTotal, tournamentId, matchKey, isFinal);
        }

        if (isFriendly) {
            // Zahtev 3e - prijateljske partije ne donose/ne oduzimaju zvezde
            tvStarsEarned.setText("Prijateljska partija - bez zvezda");
        } else {
            int starsChange = calculateStars(iWon, myTotal);
            tvStarsEarned.setText((iWon ? "+" : "") + starsChange + " ⭐");
        }

        if (!isGuest && isLoggedIn) {
            if (isFriendly) {
                // Samo otkacujemo dnevnu misiju "odigraj prijateljsku partiju", NISTA drugo
                String uid = FirebaseAuth.getInstance().getUid();
                if (uid != null) {
                    new com.example.slagalica.data.repository.MissionRepository()
                            .completeMission(uid, "friendlyMatch", new com.example.slagalica.data.repository.MissionRepository.Callback<Void>() {
                                @Override public void onSuccess(Void r) {}
                                @Override public void onError(Exception e) {}
                            });
                }
            } else {
                int starsChange = calculateStars(iWon, myTotal);
                saveResults(iWon, myTotal, starsChange);
            }
        }

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

    private void handleTournamentResult(boolean won, int myScore,
                                        String tournamentId, String matchKey, boolean isFinal) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        // Upiši pobednika u Firestore
        new com.example.slagalica.data.repository.TournamentRepository()
                .submitSemifinalResult(tournamentId, matchKey, won ? uid : null,
                        new com.example.slagalica.data.repository.TournamentRepository.Callback<Void>() {
                            @Override public void onSuccess(Void r) {}
                            @Override public void onError(Exception e) {}
                        });

        if (!won && !isFinal) return; // gubitak u polufinalu — nema nagrada

        new com.example.slagalica.data.repository.UserRepository()
                .getCurrentUser(new com.example.slagalica.data.repository.UserRepository.Callback<
                        com.example.slagalica.data.model.User>() {
            @Override
            public void onSuccess(com.example.slagalica.data.model.User user) {
                int starsChange = calculateStars(won, myScore);
                int bonusTokens = 0;
                int bonusStars  = 0;

                if (won && !isFinal) {
                    bonusTokens = 2; // pobeda u polufinalu
                } else if (won && isFinal) {
                    bonusTokens = 3; // pobeda u finalu
                    bonusStars  = 10;
                } else if (!won && isFinal) {
                    // gubitak u finalu — samo regularne zvezde
                }

                int newStars  = Math.max(0, user.getStars() + starsChange + bonusStars);
                int newTokens = user.getTokens() + bonusTokens;

                com.example.slagalica.data.repository.UserRepository repo =
                        new com.example.slagalica.data.repository.UserRepository();
                repo.updateField("stars", newStars, new com.example.slagalica.data.repository.UserRepository.Callback<Void>() {
                    @Override public void onSuccess(Void r) {}
                    @Override public void onError(Exception e) {}
                });
                if (newTokens != user.getTokens()) {
                    repo.updateField("tokens", newTokens, new com.example.slagalica.data.repository.UserRepository.Callback<Void>() {
                        @Override public void onSuccess(Void r) {}
                        @Override public void onError(Exception e) {}
                    });
                }

                // Misija "pobedi turnir"
                if (won && isFinal) {
                    new com.example.slagalica.data.repository.MissionRepository()
                            .completeMission(uid, "winTournament",
                                    new com.example.slagalica.data.repository.MissionRepository.Callback<Void>() {
                                        @Override public void onSuccess(Void r) {}
                                        @Override public void onError(Exception e) {}
                                    });
                }
            }
            @Override public void onError(Exception e) {}
        });
    }
    private void showLeagueChangeDialog(int oldLeague, int newLeague) {
        if (isFinishing() || isDestroyed()) return;

        boolean promoted = newLeague > oldLeague;
        String title = promoted ? "🎉 Napredovanje u ligi!" : "⬇️ Pad u ligi";
        String message = promoted
                ? "Prešao si u "
                  + com.example.slagalica.logic.LeagueLogic.getLeagueIcon(newLeague)
                  + " " + com.example.slagalica.logic.LeagueLogic.getLeagueName(newLeague)
                  + "!\n\nSada dobijate " + newLeague + " dodatnih tokena dnevno."
                : "Pao si na "
                  + com.example.slagalica.logic.LeagueLogic.getLeagueIcon(newLeague)
                  + " " + com.example.slagalica.logic.LeagueLogic.getLeagueName(newLeague)
                  + ".\n\nNastavi da igraš da bi se vratio!";

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();

        // Notifikacija u bazu (za slučaj da korisnik nije u appu sledeći put)
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            AppNotification notif = new AppNotification();
            notif.setUserId(uid);
            notif.setType(AppNotificationManager.TYPE_REWARD);
            notif.setMessage(message);
            notif.setRead(false);
            notif.setCreatedAt(System.currentTimeMillis());
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("notifications").add(notif);
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
                if (won) {
                    new com.example.slagalica.data.repository.MissionRepository()
                            .completeMission(user.getId(), "winMatch", new com.example.slagalica.data.repository.MissionRepository.Callback<Void>() {
                                @Override public void onSuccess(Void r) {}
                                @Override public void onError(Exception e) {}
                            });
                }
                int currentStars = user.getStars();
                int newStars = Math.max(0, currentStars + starsChange); // ne može ispod 0

                // Proveri da li 50 zvezda = 1 token
                int currentTokens = user.getTokens();
                int tokensFromStars = newStars / 50 - currentStars / 50;
                // Svaki prelaz preko multiple od 50 daje 1 token
                // Samo ako je starsChange pozitivan (pobeda ili bonus)
                int tokenBonus = Math.max(0, tokensFromStars);

                int finalNewStars = newStars; // effectively final za lambdu ispod

                userRepo.updateField("stars", newStars,
                        new UserRepository.Callback<Void>() {
                            @Override
                            public void onSuccess(Void r) {
                                // Regionalna statistika - samo POZITIVNE zvezde idu u mesečni ciklus regiona
                                if (starsChange > 0 && user.getRegion() != null) {
                                    new com.example.slagalica.data.repository.RegionStatsRepository()
                                            .addStarsToRegion(user.getRegion(), starsChange,
                                                    new com.example.slagalica.data.repository.RegionStatsRepository.Callback<Void>() {
                                                        @Override public void onSuccess(Void r2) {}
                                                        @Override public void onError(Exception e) {}
                                                    });
                                }

                                // NOVO - licna ciklusna rang lista (nedeljna/mesecna), zahtev 4a/4b
                                int leagueAtTimeOfMatch = com.example.slagalica.logic.LeagueLogic.calculateLeague(newStars);
                                new com.example.slagalica.data.repository.CycleLeaderboardRepository()
                                        .recordMatch(user.getId(), user.getUsername(), leagueAtTimeOfMatch, starsChange);
                            }
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
                int oldLeague = user.getLeague();
                int newLeague = com.example.slagalica.logic.LeagueLogic.calculateLeague(newStars);
                if (newLeague != oldLeague) {
                    userRepo.updateField("league", newLeague,
                            new UserRepository.Callback<Void>() {
                                @Override
                                public void onSuccess(Void r) {
                                    showLeagueChangeDialog(oldLeague, newLeague);
                                }
                                @Override public void onError(Exception e) {}
                            });
                }
            }
            @Override
            public void onError(Exception e) {}
        });
    }
}