package com.example.slagalica.ui.games;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.MainActivity;
import com.example.slagalica.R;
import com.example.slagalica.data.model.Korak;
import com.example.slagalica.data.model.KorakState;
import com.example.slagalica.ui.main.ResultsActivity;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class KorakPoKorakActivity extends AppCompatActivity {
    private KorakAdapter adapter;
    private ProgressBar pbTime;
    private TextView tvTimer;
    private CountDownTimer timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_korak_po_korak);

        pbTime = findViewById(R.id.pbTime);
        tvTimer = findViewById(R.id.tvTimer);
        RecyclerView rvKoraci = findViewById(R.id.rvKoraci);
        MaterialButton btnGuess = findViewById(R.id.btnGuess);
        MaterialButton btnPredaj = findViewById(R.id.btnPredaj);
        MaterialButton btnFinish = findViewById(R.id.btnFinish);

        List<Korak> koraci = buildFakeKoraci();
        adapter = new KorakAdapter(koraci);

        rvKoraci.setLayoutManager(new LinearLayoutManager(this));
        rvKoraci.setAdapter(adapter);

        startTimer();

        btnGuess.setOnClickListener(v ->
                Toast.makeText(this, "(KT1) Provera odgovora", Toast.LENGTH_SHORT).show()
        );

        btnPredaj.setOnClickListener(v -> {
            // btnPredaj i btnFinish — oba idu na MojBrojActivity
            Intent intent = new Intent(this, MojBrojActivity.class);
            intent.putExtra("isGuest",            getIntent().getBooleanExtra("isGuest", false));
            intent.putExtra("matchId",            getIntent().getStringExtra("matchId"));
            intent.putExtra("myId",               getIntent().getStringExtra("myId"));
            intent.putExtra("opponentId",         getIntent().getStringExtra("opponentId"));
            intent.putExtra("isPlayer1",          getIntent().getBooleanExtra("isPlayer1", true));
            intent.putExtra("totalMyScore",       getIntent().getIntExtra("totalMyScore", 0));
            intent.putExtra("totalOpponentScore", getIntent().getIntExtra("totalOpponentScore", 0));
            startActivity(intent);
            finish();
        });

        btnFinish.setOnClickListener(v -> {
            // btnPredaj i btnFinish — oba idu na MojBrojActivity
            Intent intent = new Intent(this, MojBrojActivity.class);
            intent.putExtra("isGuest",            getIntent().getBooleanExtra("isGuest", false));
            intent.putExtra("matchId",            getIntent().getStringExtra("matchId"));
            intent.putExtra("myId",               getIntent().getStringExtra("myId"));
            intent.putExtra("opponentId",         getIntent().getStringExtra("opponentId"));
            intent.putExtra("isPlayer1",          getIntent().getBooleanExtra("isPlayer1", true));
            intent.putExtra("totalMyScore",       getIntent().getIntExtra("totalMyScore", 0));
            intent.putExtra("totalOpponentScore", getIntent().getIntExtra("totalOpponentScore", 0));
            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        if (timer != null) timer.cancel();
        super.onDestroy();
    }

    private List<Korak> buildFakeKoraci() {
        String[] hints = new String[] {
                "voce",
                "crvena ili zelena",
                "Newton",
                "raste na drvetu",
                "Adam i Eva",
                "u tortama i pitama",
                "JA_UKA"
        };

        List<Korak> list = new ArrayList<>();
        for (int i = 0; i < hints.length; i++) {
            list.add(new Korak(i + 1, hints[i], KorakState.ZAKLJUCAN));
        }
        return list;
    }

    private void startTimer() {
        pbTime.setMax(70);
        pbTime.setProgress(70);
        timer = new CountDownTimer(70_000, 10_000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                pbTime.setProgress(seconds);
                tvTimer.setText(String.valueOf(seconds));
                adapter.otvoriSledeci();
            }

            @Override
            public void onFinish() {
                pbTime.setProgress(0);
                Toast.makeText(KorakPoKorakActivity.this,
                        getString(R.string.korak_time_up), Toast.LENGTH_SHORT).show();
            }
        };
        timer.start();
    }
}