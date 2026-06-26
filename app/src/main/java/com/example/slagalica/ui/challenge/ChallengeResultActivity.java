package com.example.slagalica.ui.challenge;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.slagalica.R;
import com.example.slagalica.data.model.Challenge;
import com.example.slagalica.data.repository.ChallengeRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChallengeResultActivity extends AppCompatActivity {

    private LinearLayout llResults;
    private TextView tvWaiting;
    private final ChallengeRepository repo = new ChallengeRepository();
    private final UserRepository userRepo = new UserRepository();
    private ListenerRegistration listener;
    private String challengeId, myUid;
    private int starsWager, tokensWager;
    private boolean rewardsDistributed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_result);

        challengeId  = getIntent().getStringExtra("challengeId");
        starsWager   = getIntent().getIntExtra("starsWager", 0);
        tokensWager  = getIntent().getIntExtra("tokensWager", 0);
        myUid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();

        llResults = findViewById(R.id.llChallengeResults);
        tvWaiting = findViewById(R.id.tvWaiting);
        findViewById(R.id.btnBackHome).setOnClickListener(v -> finish());

        // Real-time listen — čekamo dok svi ne završe
        listener = repo.listenToChallenge(challengeId,
                new ChallengeRepository.Callback<Challenge>() {
                    @Override
                    public void onSuccess(Challenge c) { updateResults(c); }
                    @Override
                    public void onError(Exception e) {}
                });
    }

    private void updateResults(Challenge c) {
        Map<String, Challenge.ParticipantResult> parts = c.getParticipants();
        if (parts == null) return;

        // Sortiraj po score-u
        List<Map.Entry<String, Challenge.ParticipantResult>> sorted = new ArrayList<>(parts.entrySet());
        sorted.sort((a, b) -> b.getValue().score - a.getValue().score);

        long finishedCount = sorted.stream().filter(e -> e.getValue().finished).count();
        boolean allDone = finishedCount == sorted.size();

        tvWaiting.setVisibility(allDone ? View.GONE : View.VISIBLE);
        tvWaiting.setText("Čekamo još " + (sorted.size() - finishedCount) + " igrača...");

        llResults.removeAllViews();
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Challenge.ParticipantResult> entry = sorted.get(i);
            View row = LayoutInflater.from(this)
                    .inflate(R.layout.item_challenge_result, llResults, false);

            String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : i == 2 ? "🥉" : "4.";
            ((TextView) row.findViewById(R.id.tvCRRank)).setText(medal);
            ((TextView) row.findViewById(R.id.tvCRUsername)).setText(entry.getValue().username);
            String scoreText = entry.getValue().finished
                    ? String.valueOf(entry.getValue().score) : "...";
            ((TextView) row.findViewById(R.id.tvCRScore)).setText(scoreText + " bodova");

            if (entry.getKey().equals(myUid)) {
                row.setBackgroundColor(0xFFFFF6D9);
            }
            llResults.addView(row);
        }

        if (allDone && !rewardsDistributed) {
            rewardsDistributed = true;
            distributeRewards(sorted, c);
        }
    }

    private void distributeRewards(
            List<Map.Entry<String, Challenge.ParticipantResult>> sorted, Challenge c) {

        int n = sorted.size();
        int totalStars  = c.getStarsWager()  * n;
        int totalTokens = c.getTokensWager() * n;

        // 1. mesto — 75% ukupnog uloga
        // 2. mesto — dobija nazad uloženo
        // ostali — gube ulog

        for (int i = 0; i < sorted.size(); i++) {
            String uid = sorted.get(i).getKey();
            boolean isMe = uid.equals(myUid);
            if (!isMe) continue;

            final int rank = i; // DODATO

            int starsReward  = 0;
            int tokensReward = 0;

            if (rank == 0) {
                starsReward  = (int) Math.round(totalStars  * 0.75);
                tokensReward = (int) Math.round(totalTokens * 0.75);
            } else if (rank == 1) {
                starsReward  = c.getStarsWager();
                tokensReward = c.getTokensWager();
            }

            if (starsReward > 0 || tokensReward > 0) {
                int finalStars  = starsReward;
                int finalTokens = tokensReward;
                userRepo.getCurrentUser(new UserRepository.Callback<com.example.slagalica.data.model.User>() {
                    @Override
                    public void onSuccess(com.example.slagalica.data.model.User user) {
                        userRepo.updateField("stars",
                                user.getStars() + finalStars, new UserRepository.Callback<Void>() {
                                    @Override public void onSuccess(Void r) {}
                                    @Override public void onError(Exception e) {}
                                });
                        if (finalTokens > 0) {
                            userRepo.updateField("tokens",
                                    user.getTokens() + finalTokens, new UserRepository.Callback<Void>() {
                                        @Override public void onSuccess(Void r) {}
                                        @Override public void onError(Exception e) {}
                                    });
                        }
                        String msg = rank == 0  // PROMENJENO i -> rank
                                ? "🥇 Pobedio si izazov! +" + finalStars + "⭐ +" + finalTokens + "🪙"
                                : "🥈 Vratio si ulog: +" + finalStars + "⭐ +" + finalTokens + "🪙";
                        Toast.makeText(ChallengeResultActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                    @Override public void onError(Exception e) {}
                });
            }
        }

        // Označi izazov kao završen
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("challenges").document(challengeId)
                .update("status", "finished");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null) listener.remove();
    }
}