package com.example.slagalica.ui.challenge;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.slagalica.R;
import com.example.slagalica.data.model.Challenge;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.ChallengeRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.List;
import java.util.Map;

public class ChallengeActivity extends AppCompatActivity {

    private LinearLayout llChallenges;
    private final ChallengeRepository repo = new ChallengeRepository();
    private final UserRepository userRepo = new UserRepository();
    private User currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge);

        llChallenges = findViewById(R.id.llChallenges);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCreateChallenge).setOnClickListener(v -> showCreateDialog());

        userRepo.getCurrentUser(new UserRepository.Callback<User>() {
            @Override
            public void onSuccess(User user) {
                currentUser = user;
                loadChallenges();
            }
            @Override
            public void onError(Exception e) { finish(); }
        });
    }

    private void loadChallenges() {
        if (currentUser.getRegion() == null) {
            Toast.makeText(this, "Niste odabrali region.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        repo.getOpenChallengesForRegion(currentUser.getRegion(),
                new ChallengeRepository.Callback<List<Challenge>>() {
                    @Override
                    public void onSuccess(List<Challenge> list) {
                        llChallenges.removeAllViews();
                        if (list.isEmpty()) {
                            TextView tv = new TextView(ChallengeActivity.this);
                            tv.setText("Nema aktivnih izazova u vašem regionu.");
                            tv.setPadding(16, 16, 16, 16);
                            llChallenges.addView(tv);
                            return;
                        }
                        for (Challenge c : list) addChallengeCard(c);
                    }
                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(ChallengeActivity.this,
                                "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void addChallengeCard(Challenge c) {
        View card = LayoutInflater.from(this)
                .inflate(R.layout.item_challenge, llChallenges, false);

        ((TextView) card.findViewById(R.id.tvChallengeCreator))
                .setText("👤 " + c.getCreatorUsername());
        ((TextView) card.findViewById(R.id.tvChallengeWager))
                .setText("Ulog: " + c.getStarsWager() + " ⭐  " + c.getTokensWager() + " 🪙");
        int count = c.getParticipants() != null ? c.getParticipants().size() : 1;
        ((TextView) card.findViewById(R.id.tvChallengeSlots))
                .setText("Igrači: " + count + "/4");

        MaterialButton btnJoin = card.findViewById(R.id.btnJoinChallenge);

        boolean alreadyIn = c.getParticipants() != null
                && c.getParticipants().containsKey(currentUser.getId());
        boolean isCreator = c.getCreatorId().equals(currentUser.getId());

        if (alreadyIn) {
            btnJoin.setText("Odigraj");
            btnJoin.setOnClickListener(v -> startChallengeGame(c));
        } else {
            btnJoin.setText("Prihvati");
            btnJoin.setOnClickListener(v -> joinAndPlay(c));
        }

        llChallenges.addView(card);
    }

    private void showCreateDialog() {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_create_challenge, null);

        Slider sliderStars  = dialogView.findViewById(R.id.sliderStars);
        Slider sliderTokens = dialogView.findViewById(R.id.sliderTokens);
        TextView tvStarsVal  = dialogView.findViewById(R.id.tvStarsValue);
        TextView tvTokensVal = dialogView.findViewById(R.id.tvTokensValue);

        sliderStars.setValueFrom(1); sliderStars.setValueTo(10); sliderStars.setValue(5);
        sliderTokens.setValueFrom(0); sliderTokens.setValueTo(2); sliderTokens.setValue(1);

        sliderStars.addOnChangeListener((s, val, f) -> tvStarsVal.setText((int)val + " ⭐"));
        sliderTokens.addOnChangeListener((s, val, f) -> tvTokensVal.setText((int)val + " 🪙"));

        tvStarsVal.setText("5 ⭐");
        tvTokensVal.setText("1 🪙");

        new AlertDialog.Builder(this)
                .setTitle("Postavi izazov")
                .setView(dialogView)
                .setPositiveButton("Postavi", (d, w) -> {
                    int stars  = (int) sliderStars.getValue();
                    int tokens = (int) sliderTokens.getValue();

                    if (currentUser.getStars() < stars || currentUser.getTokens() < tokens) {
                        Toast.makeText(this, "Nemate dovoljno zvezda/tokena.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    createChallenge(stars, tokens);
                })
                .setNegativeButton("Otkaži", null)
                .show();
    }

    private void createChallenge(int stars, int tokens) {
        repo.createChallenge(
                currentUser.getId(), currentUser.getUsername(),
                currentUser.getRegion(), stars, tokens,
                new ChallengeRepository.Callback<String>() {
                    @Override
                    public void onSuccess(String challengeId) {
                        // Oduzmi ulog kreatoru
                        deductWager(stars, tokens, () -> {
                            Toast.makeText(ChallengeActivity.this,
                                    "Izazov postavljen!", Toast.LENGTH_SHORT).show();
                            loadChallenges();
                            // Odmah otvori ekran za igranje (kreator čeka ili odmah igra)
                            openChallengeGame(challengeId, stars, tokens);
                        });
                    }
                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(ChallengeActivity.this,
                                "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void joinAndPlay(Challenge c) {
        if (currentUser.getStars() < c.getStarsWager()
                || currentUser.getTokens() < c.getTokensWager()) {
            Toast.makeText(this, "Nemate dovoljno zvezda/tokena za ovaj izazov.", Toast.LENGTH_SHORT).show();
            return;
        }
        repo.joinChallenge(c.getId(), currentUser.getId(), currentUser.getUsername(),
                new ChallengeRepository.Callback<Void>() {
                    @Override
                    public void onSuccess(Void r) {
                        deductWager(c.getStarsWager(), c.getTokensWager(), () ->
                                openChallengeGame(c.getId(), c.getStarsWager(), c.getTokensWager()));
                    }
                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(ChallengeActivity.this,
                                "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void startChallengeGame(Challenge c) {
        openChallengeGame(c.getId(), c.getStarsWager(), c.getTokensWager());
    }

    private void openChallengeGame(String challengeId, int stars, int tokens) {
        Intent i = new Intent(this, ChallengeGameActivity.class);
        i.putExtra("challengeId", challengeId);
        i.putExtra("starsWager", stars);
        i.putExtra("tokensWager", tokens);
        startActivity(i);
    }

    private void deductWager(int stars, int tokens, Runnable onDone) {
        userRepo.updateField("stars",
                Math.max(0, currentUser.getStars() - stars),
                new UserRepository.Callback<Void>() {
                    @Override public void onSuccess(Void r) {
                        userRepo.updateField("tokens",
                                Math.max(0, currentUser.getTokens() - tokens),
                                new UserRepository.Callback<Void>() {
                                    @Override public void onSuccess(Void r2) { onDone.run(); }
                                    @Override public void onError(Exception e) { onDone.run(); }
                                });
                    }
                    @Override public void onError(Exception e) { onDone.run(); }
                });
    }
}