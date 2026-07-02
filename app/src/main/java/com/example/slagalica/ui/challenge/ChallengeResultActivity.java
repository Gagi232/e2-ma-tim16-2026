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

        List<Map.Entry<String, Challenge.ParticipantResult>> sorted = new ArrayList<>(parts.entrySet());
        sorted.sort((a, b) -> {
            int scoreA = a.getValue().finished ? a.getValue().score : -1;
            int scoreB = b.getValue().finished ? b.getValue().score : -1;
            return Integer.compare(scoreB, scoreA);
        });

        long finishedCount = sorted.stream().filter(e -> e.getValue().finished).count();
        int totalCount = sorted.size();

        // Prikaži status čekanja
        boolean allDone = finishedCount == totalCount;
        tvWaiting.setVisibility(allDone ? View.GONE : View.VISIBLE);
        tvWaiting.setText("Čekamo još " + (totalCount - finishedCount) + " igrača...");

        llResults.removeAllViews();
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Challenge.ParticipantResult> entry = sorted.get(i);
            View row = LayoutInflater.from(this)
                    .inflate(R.layout.item_challenge_result, llResults, false);

            String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : i == 2 ? "🥉" : "4.";
            ((TextView) row.findViewById(R.id.tvCRRank)).setText(medal);
            ((TextView) row.findViewById(R.id.tvCRUsername)).setText(entry.getValue().username);

            // Prikaži "..." dok igrač nije završio
            String scoreText = entry.getValue().finished
                    ? String.valueOf(entry.getValue().score) : "⏳";
            ((TextView) row.findViewById(R.id.tvCRScore)).setText(scoreText + " bodova");

            if (entry.getKey().equals(myUid)) {
                row.setBackgroundColor(0xFFFFF6D9);
            }
            llResults.addView(row);
        }

        // Nagrade samo kad SVI završe I ima bar 2 igrača
        if (allDone && !rewardsDistributed && totalCount >= 2) {
            rewardsDistributed = true;
            distributeRewards(sorted, c);
        } else if (allDone && !rewardsDistributed && totalCount == 1) {
            // Jedini igrač — čekaj malo, možda neko još nije ni ušao
            // Ne distribuiraj odmah
            tvWaiting.setVisibility(View.VISIBLE);
            tvWaiting.setText("Čekamo da se drugi igrači pridruže...");
        }
    }

    private void distributeRewards(
            List<Map.Entry<String, Challenge.ParticipantResult>> sorted, Challenge c) {

        // Uzmi samo finished igrače za nagrade
        List<Map.Entry<String, Challenge.ParticipantResult>> finishedSorted = new ArrayList<>();
        for (Map.Entry<String, Challenge.ParticipantResult> entry : sorted) {
            if (entry.getValue().finished) finishedSorted.add(entry);
        }

        int n = finishedSorted.size();
        int totalStars  = c.getStarsWager() * c.getParticipants().size(); // ukupan ulog SVIH
        int totalTokens = c.getTokensWager() * c.getParticipants().size();

        for (int i = 0; i < finishedSorted.size(); i++) {
            String uid = finishedSorted.get(i).getKey();
            if (!uid.equals(myUid)) continue;

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