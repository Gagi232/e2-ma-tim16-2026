package com.example.slagalica.ui.tournament;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.slagalica.R;
import com.example.slagalica.ui.games.KoZnaZnaActivity;
import com.google.android.material.button.MaterialButton;

public class TournamentActivity extends AppCompatActivity {

    private static final int PHASE_WAITING = 0;
    private static final int PHASE_SEMIFINAL = 1;
    private static final int PHASE_FINAL = 2;

    private int currentPhase = PHASE_WAITING;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tournament);

        MaterialButton btnJoin = findViewById(R.id.btnJoin);
        MaterialButton btnQuit = findViewById(R.id.btnQuit);
        LinearLayout llWaiting = findViewById(R.id.llWaiting);
        LinearLayout llBracket = findViewById(R.id.llBracket);

        btnJoin.setOnClickListener(v -> {
            // Simulacija čekanja igrača
            btnJoin.setEnabled(false);
            btnJoin.setText("Čekanje igrača...");
            llWaiting.setVisibility(View.VISIBLE);

            new Handler().postDelayed(() -> {
                // Nakon 2 sekunde simuliramo da su se spojili igrači
                llWaiting.setVisibility(View.GONE);
                llBracket.setVisibility(View.VISIBLE);
                currentPhase = PHASE_SEMIFINAL;
                prikaziSpajanje();
            }, 2000);
        });

        btnQuit.setOnClickListener(v -> finish());
    }

    private void prikaziSpajanje() {
        LinearLayout llBracket = findViewById(R.id.llBracket);
        llBracket.setVisibility(View.VISIBLE);

        MaterialButton btnPlay = findViewById(R.id.btnPlayTournament);
        btnPlay.setVisibility(View.VISIBLE);

        btnPlay.setOnClickListener(v -> {
            Intent intent = new Intent(this, KoZnaZnaActivity.class);
            intent.putExtra("isTournament", true);
            startActivity(intent);
        });
    }
}