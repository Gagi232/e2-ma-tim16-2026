package com.example.slagalica.ui.tournament;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.data.repository.TournamentRepository;
import com.example.slagalica.ui.games.KoZnaZnaActivity;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;
import java.util.Map;

public class TournamentActivity extends AppCompatActivity {

    private final TournamentRepository repo = new TournamentRepository();
    private String tournamentId;
    private ListenerRegistration registration;

    private MaterialButton btnJoin, btnQuit, btnPlay;
    private LinearLayout llWaiting, llBracket;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tournament);

        btnJoin = findViewById(R.id.btnJoin);
        btnQuit = findViewById(R.id.btnQuit);
        btnPlay = findViewById(R.id.btnPlayTournament);
        llWaiting = findViewById(R.id.llWaiting);
        llBracket = findViewById(R.id.llBracket);
        tvStatus = findViewById(R.id.tvTournamentStatus);

        btnJoin.setOnClickListener(v -> joinTournament());
        btnQuit.setOnClickListener(v -> finish());
        btnPlay.setVisibility(View.GONE);
    }

    private void joinTournament() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        btnJoin.setEnabled(false);
        repo.joinQueue(uid, new TournamentRepository.Callback<String>() {
            @Override
            public void onSuccess(String id) {
                tournamentId = id;
                llWaiting.setVisibility(View.VISIBLE);
                listenToTournament();
            }
            @Override public void onError(Exception e) {
                btnJoin.setEnabled(true);
                Toast.makeText(TournamentActivity.this, "Greška!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void listenToTournament() {
        registration = repo.listenToTournament(tournamentId, new TournamentRepository.Callback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                List<String> players = (List<String>) data.get("players");
                String status = (String) data.get("status");

                if (players != null) tvStatus.setText("Igrača: " + players.size() + "/4");

                if ("semifinals".equals(status)) {
                    llWaiting.setVisibility(View.GONE);
                    llBracket.setVisibility(View.VISIBLE);
                    btnPlay.setVisibility(View.VISIBLE);
                    btnPlay.setOnClickListener(v -> {
                        Intent i = new Intent(TournamentActivity.this, KoZnaZnaActivity.class);
                        i.putExtra("isTournament", true);
                        i.putExtra("tournamentId", tournamentId);
                        startActivity(i);
                    });
                } else if (players != null && players.size() == 4 && "waiting".equals(status)) {
                    repo.startTournament(tournamentId, players);
                }
            }
            @Override public void onError(Exception e) {}
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (registration != null) registration.remove();
    }
}