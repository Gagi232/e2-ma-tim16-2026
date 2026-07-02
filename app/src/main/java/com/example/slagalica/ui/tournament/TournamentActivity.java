package com.example.slagalica.ui.tournament;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.slagalica.R;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.TournamentRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.logic.LeagueLogic;
import com.example.slagalica.ui.games.KoZnaZnaActivity;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TournamentActivity extends AppCompatActivity {

    private static final int TOURNAMENT_COST = 3;

    private final TournamentRepository repo = new TournamentRepository();
    private final UserRepository userRepo = new UserRepository();

    private String tournamentId, myUid, myMatchKey;
    private boolean inQueue = false;
    private ListenerRegistration registration;

    // UI
    private MaterialButton btnJoin, btnQuit, btnPlay;
    private LinearLayout llWaiting, llBracket;
    private TextView tvStatus;

    // Bracket views — polufinale 1
    private TextView tvSF1P1Name, tvSF1P1League, tvSF1P1Avatar;
    private TextView tvSF1P2Name, tvSF1P2League, tvSF1P2Avatar;
    // Polufinale 2
    private TextView tvSF2P1Name, tvSF2P1League, tvSF2P1Avatar;
    private TextView tvSF2P2Name, tvSF2P2League, tvSF2P2Avatar;
    // Finale
    private TextView tvFP1Name, tvFP1Avatar, tvFP2Name, tvFP2Avatar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tournament);

        myUid = FirebaseAuth.getInstance().getUid();

        btnJoin  = findViewById(R.id.btnJoin);
        btnQuit  = findViewById(R.id.btnQuit);
        btnPlay  = findViewById(R.id.btnPlayTournament);
        llWaiting = findViewById(R.id.llWaiting);
        llBracket = findViewById(R.id.llBracket);
        tvStatus  = findViewById(R.id.tvTournamentStatus);

        // Bracket TextViews — moraju biti u XML-u
        tvSF1P1Name   = findViewById(R.id.tvSF1P1Name);
        tvSF1P1League = findViewById(R.id.tvSF1P1League);
        tvSF1P1Avatar = findViewById(R.id.tvSF1P1Avatar);
        tvSF1P2Name   = findViewById(R.id.tvSF1P2Name);
        tvSF1P2League = findViewById(R.id.tvSF1P2League);
        tvSF1P2Avatar = findViewById(R.id.tvSF1P2Avatar);
        tvSF2P1Name   = findViewById(R.id.tvSF2P1Name);
        tvSF2P1League = findViewById(R.id.tvSF2P1League);
        tvSF2P1Avatar = findViewById(R.id.tvSF2P1Avatar);
        tvSF2P2Name   = findViewById(R.id.tvSF2P2Name);
        tvSF2P2League = findViewById(R.id.tvSF2P2League);
        tvSF2P2Avatar = findViewById(R.id.tvSF2P2Avatar);
        tvFP1Name     = findViewById(R.id.tvFP1Name);
        tvFP1Avatar   = findViewById(R.id.tvFP1Avatar);
        tvFP2Name     = findViewById(R.id.tvFP2Name);
        tvFP2Avatar   = findViewById(R.id.tvFP2Avatar);

        btnPlay.setVisibility(View.GONE);
        btnJoin.setOnClickListener(v -> joinTournament());
        btnQuit.setOnClickListener(v -> leaveTournament());
    }

    private void joinTournament() {
        if (myUid == null) return;
        btnJoin.setEnabled(false);

        userRepo.getCurrentUser(new UserRepository.Callback<User>() {
            @Override
            public void onSuccess(User user) {
                if (user.getTokens() < TOURNAMENT_COST) {
                    Toast.makeText(TournamentActivity.this,
                            "Nemate dovoljno tokena (potrebno " + TOURNAMENT_COST + ").",
                            Toast.LENGTH_SHORT).show();
                    btnJoin.setEnabled(true);
                    return;
                }
                // Oduzmi tokene
                userRepo.updateField("tokens", user.getTokens() - TOURNAMENT_COST,
                        new UserRepository.Callback<Void>() {
                            @Override public void onSuccess(Void r) {
                                Map<String, Object> playerData = new HashMap<>();
                                playerData.put("username", user.getUsername());
                                playerData.put("league", user.getLeague());
                                playerData.put("avatar", user.getAvatarUrl() != null
                                        ? user.getAvatarUrl() : "👤");

                                repo.joinQueue(myUid, playerData,
                                        new TournamentRepository.Callback<String>() {
                                            @Override public void onSuccess(String id) {
                                                tournamentId = id;
                                                inQueue = true;
                                                llWaiting.setVisibility(View.VISIBLE);
                                                btnJoin.setVisibility(View.GONE);
                                                listenToTournament();
                                            }
                                            @Override public void onError(Exception e) {
                                                // Vrati tokene ako nije uspelo
                                                userRepo.updateField("tokens",
                                                        user.getTokens(), new UserRepository.Callback<Void>() {
                                                            @Override public void onSuccess(Void r) {}
                                                            @Override public void onError(Exception ex) {}
                                                        });
                                                btnJoin.setEnabled(true);
                                                Toast.makeText(TournamentActivity.this,
                                                        "Greška pri ulasku.", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                            }
                            @Override public void onError(Exception e) {
                                btnJoin.setEnabled(true);
                            }
                        });
            }
            @Override public void onError(Exception e) { btnJoin.setEnabled(true); }
        });
    }

    private void leaveTournament() {
        if (tournamentId != null && inQueue) {
            repo.leaveQueue(tournamentId, myUid);
        }
        finish();
    }

    private void listenToTournament() {
        registration = repo.listenToTournament(tournamentId,
                new TournamentRepository.Callback<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot snap) {
                        onTournamentUpdate(snap);
                    }
                    @Override public void onError(Exception e) {}
                });
    }

    private void onTournamentUpdate(DocumentSnapshot snap) {
        String status = snap.getString("status");
        List<String> players = (List<String>) snap.get("players");
        Map<String, Object> playerData = (Map<String, Object>) snap.get("playerData");

        if ("waiting".equals(status)) {
            int count = players != null ? players.size() : 0;
            tvStatus.setText("Igrači: " + count + "/4 — čekamo...");
            return;
        }

        // Semifinals ili finals — prikaži bracket
        llWaiting.setVisibility(View.GONE);
        llBracket.setVisibility(View.VISIBLE);

        Map<String, Object> sf1 = (Map<String, Object>) snap.get("semifinal1");
        Map<String, Object> sf2 = (Map<String, Object>) snap.get("semifinal2");
        Map<String, Object> finalMatch = (Map<String, Object>) snap.get("final");

        if (sf1 != null && playerData != null) {
            fillPlayer(tvSF1P1Name, tvSF1P1League, tvSF1P1Avatar,
                    (String) sf1.get("player1"), playerData);
            fillPlayer(tvSF1P2Name, tvSF1P2League, tvSF1P2Avatar,
                    (String) sf1.get("player2"), playerData);
        }
        if (sf2 != null && playerData != null) {
            fillPlayer(tvSF2P1Name, tvSF2P1League, tvSF2P1Avatar,
                    (String) sf2.get("player1"), playerData);
            fillPlayer(tvSF2P2Name, tvSF2P2League, tvSF2P2Avatar,
                    (String) sf2.get("player2"), playerData);
        }

        if ("semifinals".equals(status)) {
            // Nađi moj meč
            myMatchKey = null;
            if (sf1 != null && (myUid.equals(sf1.get("player1")) || myUid.equals(sf1.get("player2"))))
                myMatchKey = "semifinal1";
            else if (sf2 != null && (myUid.equals(sf2.get("player1")) || myUid.equals(sf2.get("player2"))))
                myMatchKey = "semifinal2";

            if (myMatchKey != null) {
                btnPlay.setVisibility(View.VISIBLE);
                btnPlay.setText("Igraj polufinale! ⚔️");
                Map<String, Object> myMatch = "semifinal1".equals(myMatchKey) ? sf1 : sf2;
                String opponentId = myUid.equals(myMatch.get("player1"))
                        ? (String) myMatch.get("player2")
                        : (String) myMatch.get("player1");
                btnPlay.setOnClickListener(v -> startMatch(opponentId, myMatchKey, false));
            }
        } else if ("finals".equals(status) && finalMatch != null) {
            String w1 = (String) finalMatch.get("player1");
            String w2 = (String) finalMatch.get("player2");
            if (playerData != null) {
                fillPlayerSimple(tvFP1Name, tvFP1Avatar, w1, playerData);
                fillPlayerSimple(tvFP2Name, tvFP2Avatar, w2, playerData);
            }

            String finalWinner = (String) finalMatch.get("winnerId");
            if (finalWinner != null) {
                showFinalResult(finalWinner, snap);
                return;
            }

            if (myUid.equals(w1) || myUid.equals(w2)) {
                String opponent = myUid.equals(w1) ? w2 : w1;
                btnPlay.setVisibility(View.VISIBLE);
                btnPlay.setText("Igraj finale! 🏆");
                btnPlay.setOnClickListener(v -> startMatch(opponent, "final", true));
            } else {
                btnPlay.setVisibility(View.GONE);
                tvStatus.setText("Eliminisan. Čekaj kraj finala...");
                tvStatus.setVisibility(View.VISIBLE);
            }
        } else if ("finished".equals(status)) {
            showFinalResult((String) finalMatch.get("winnerId"), snap);
        }
    }

    private void startMatch(String opponentId, String matchKey, boolean isFinal) {
        btnPlay.setEnabled(false);
        Intent intent = new Intent(this, KoZnaZnaActivity.class);
        intent.putExtra("isGuest", false);
        intent.putExtra("matchId", tournamentId + "_" + matchKey);
        intent.putExtra("myId", myUid);
        intent.putExtra("opponentId", opponentId);
        intent.putExtra("isPlayer1", myUid.compareTo(opponentId) < 0);
        intent.putExtra("isFriendly", false);
        intent.putExtra("isTournament", true);
        intent.putExtra("tournamentId", tournamentId);
        intent.putExtra("tournamentMatchKey", matchKey);
        intent.putExtra("isTournamentFinal", isFinal);
        startActivity(intent);
    }

    private void showFinalResult(String winnerId, DocumentSnapshot snap) {
        btnPlay.setVisibility(View.GONE);
        llWaiting.setVisibility(View.GONE);

        boolean iWon = myUid.equals(winnerId);
        tvStatus.setVisibility(View.VISIBLE);

        if (iWon) {
            tvStatus.setText("🏆 Čestitamo! Pobedio si turnir!");
            tvStatus.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in));
        } else {
            Map<String, Object> finalMatch = (Map<String, Object>) snap.get("final");
            boolean iWasInFinal = finalMatch != null
                    && (myUid.equals(finalMatch.get("player1"))
                    || myUid.equals(finalMatch.get("player2")));
            tvStatus.setText(iWasInFinal
                    ? "😔 Izgubio si finale. Dobro si se borio!"
                    : "Turnir završen.");
        }
    }

    private void fillPlayer(TextView tvName, TextView tvLeague, TextView tvAvatar,
                            String uid, Map<String, Object> playerData) {
        if (uid == null || playerData == null) return;
        Map<String, Object> data = (Map<String, Object>) playerData.get(uid);
        if (data == null) return;
        String name = (String) data.get("username");
        Long league = (Long) data.get("league");
        String avatar = (String) data.get("avatar");
        tvName.setText(uid.equals(myUid) ? "Ti (" + name + ")" : name);
        tvLeague.setText(LeagueLogic.getLeagueIcon(league != null ? league.intValue() : 0)
                + " " + LeagueLogic.getLeagueName(league != null ? league.intValue() : 0));
        tvAvatar.setText(avatar != null ? avatar : "👤");
    }

    private void fillPlayerSimple(TextView tvName, TextView tvAvatar,
                                  String uid, Map<String, Object> playerData) {
        if (uid == null || playerData == null) return;
        Map<String, Object> data = (Map<String, Object>) playerData.get(uid);
        if (data == null) return;
        tvName.setText(uid.equals(myUid) ? "Ti" : (String) data.get("username"));
        tvAvatar.setText((String) data.getOrDefault("avatar", "👤"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (registration != null) registration.remove();
    }
}