package com.example.slagalica.ui.games;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.data.model.Korak;
import com.example.slagalica.data.model.KorakState;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class KorakPoKorakActivity extends AppCompatActivity {

    // Svaka slagalica: {tacan_odgovor, korak1, korak2, ..., korak7}
    private static final String[][] PUZZLES = {
        {"jabuka",    "voce",      "crvena ili zelena", "Newton",   "raste na drvetu", "Adam i Eva",        "u tortama",   "J_A_U_A"},
        {"suncokret", "biljka",    "seme",              "zuta",     "okrenuto ka suncu","Van Gogh",          "ulje",        "SUNCOKRET"},
        {"voda",      "tecnost",   "providna",          "H2O",      "okean",            "zedja",             "led ili para","VO_A"},
        {"knjiga",    "papir",     "pisanje",           "biblioteka","Gutenberg",        "ucenje",            "autor",       "KNJ_GA"}
    };

    // UI
    private boolean isFriendly;
    private ProgressBar pbTime;
    private TextView tvTimer, tvMyScore, tvOppScore, tvRunda;
    private RecyclerView rvKoraci;
    private TextInputEditText etGuess;
    private MaterialButton btnGuess, btnPredaj, btnFinish;

    // Stanje igre
    private List<Korak> koraci;
    private KorakAdapter adapter;
    private String correctAnswer;
    private int openedSteps = 0;      // koliko je koraka otvoreno
    private boolean roundActive = false;
    private boolean bonusPhase = false;

    // Timeri
    private CountDownTimer roundTimer;
    private CountDownTimer bonusTimer;

    // Runde i bodovi
    private int currentRound = 1;
    private int myTotalScore  = 0;  // bodovi samo za ovu igru (prikaz)
    private int oppTotalScore = 0;
    private int prevMyScore   = 0;  // kumulativ iz prethodnih igara
    private int prevOppScore  = 0;

    // Match parametri
    private boolean isPlayer1, isGuest;
    private String matchId, myId, opponentId;

    private final Random random = new Random();
    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_korak_po_korak);

        isPlayer1     = getIntent().getBooleanExtra("isPlayer1", true);
        isGuest       = getIntent().getBooleanExtra("isGuest", false);
        matchId       = getIntent().getStringExtra("matchId");
        myId          = getIntent().getStringExtra("myId");
        isFriendly = getIntent().getBooleanExtra("isFriendly", false);
        opponentId    = getIntent().getStringExtra("opponentId");
        prevMyScore  = getIntent().getIntExtra("totalMyScore", 0);
        prevOppScore = getIntent().getIntExtra("totalOpponentScore", 0);

        bindViews();
        startRound(1);
    }

    private void bindViews() {
        pbTime    = findViewById(R.id.pbTime);
        tvTimer   = findViewById(R.id.tvTimer);
        tvMyScore = findViewById(R.id.tvMyScore);
        tvOppScore= findViewById(R.id.tvOpponentScore);
        tvRunda   = findViewById(R.id.tvRunda);
        rvKoraci  = findViewById(R.id.rvKoraci);
        etGuess   = findViewById(R.id.etGuess);
        btnGuess  = findViewById(R.id.btnGuess);
        btnPredaj = findViewById(R.id.btnPredaj);
        btnFinish = findViewById(R.id.btnFinish);

        rvKoraci.setLayoutManager(new LinearLayoutManager(this));

        btnGuess.setOnClickListener(v -> handleGuess());

        btnPredaj.setOnClickListener(v -> {
            if (!roundActive || bonusPhase) return;
            stopRoundTimer();
            roundActive = false;
            markRemainingAsMissed();
            boolean iAmActive = isActiveThisRound();
            if (iAmActive) startOpponentBonusPhase();
            else           startMyBonusPhase();
        });

        btnFinish.setOnClickListener(v -> goToMojBroj());
    }

    // ── Upravljanje rundama ────────────────────────────────────────────────────

    private void startRound(int round) {
        currentRound = round;
        openedSteps  = 0;
        roundActive  = true;
        bonusPhase   = false;

        // Odaberi slagalicu za ovu rundu (iz PUZZLES niza)
        int puzzleIndex = (round - 1) % PUZZLES.length;
        String[] puzzle = PUZZLES[puzzleIndex];
        correctAnswer   = puzzle[0];

        // Napravi listu koraka
        koraci = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            koraci.add(new Korak(i, puzzle[i], KorakState.ZAKLJUCAN));
        }
        adapter = new KorakAdapter(koraci);
        rvKoraci.setAdapter(adapter);

        tvRunda.setText("Runda " + round + "/2");
        etGuess.setText("");
        refreshScores();

        boolean iAmActive = isActiveThisRound();
        setInputEnabled(iAmActive);

        // Odmah otvori prvi korak
        openNextStep();
        startRoundTimer();
    }

    private boolean isActiveThisRound() {
        // U guest/solo modu oba igraca je isti korisnik — uvek aktivan
        if (isGuest || matchId == null) return true;
        return (currentRound == 1) == isPlayer1;
    }

    private void openNextStep() {
        if (openedSteps < koraci.size()) {
            koraci.get(openedSteps).setState(KorakState.OTVOREN);
            adapter.notifyItemChanged(openedSteps);
            openedSteps++;
        }
    }

    // ── Timer runde (70s, svaki korak se otvara na 10s) ───────────────────────

    private void startRoundTimer() {
        stopRoundTimer();
        pbTime.setMax(70);
        pbTime.setProgress(70);
        tvTimer.setText("70");

        roundTimer = new CountDownTimer(70_000, 10_000) {
            @Override
            public void onTick(long ms) {
                int seconds = (int) (ms / 1000);
                pbTime.setProgress(seconds);
                tvTimer.setText(String.valueOf(seconds));
                if (roundActive) openNextStep();
            }

            @Override
            public void onFinish() {
                pbTime.setProgress(0);
                tvTimer.setText("0");
                if (roundActive) {
                    roundActive = false;
                    markRemainingAsMissed();
                    boolean iAmActive = isActiveThisRound();
                    if (iAmActive) startOpponentBonusPhase();
                    else           startMyBonusPhase();
                }
            }
        }.start();
    }

    private void stopRoundTimer() {
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
    }

    // ── Provera odgovora ───────────────────────────────────────────────────────

    private void handleGuess() {
        if (bonusPhase) {
            handleBonusGuess();
            return;
        }
        if (!roundActive || !isActiveThisRound()) return;

        String guess = etGuess.getText() != null
                ? etGuess.getText().toString().trim()
                : "";
        if (guess.isEmpty()) {
            Toast.makeText(this, "Unesite odgovor!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (guess.equalsIgnoreCase(correctAnswer)) {
            // Bodovi: korak 1 = 20, korak 2 = 18, ..., korak 7 = 8
            int score = Math.max(0, 20 - 2 * (openedSteps - 1));

            stopRoundTimer();
            roundActive = false;

            // Označi poslednji otvoreni korak kao pogođen
            int hitIndex = openedSteps - 1;
            if (hitIndex >= 0 && hitIndex < koraci.size()) {
                koraci.get(hitIndex).setState(KorakState.POGODJEN);
                adapter.notifyItemChanged(hitIndex);
            }

            myTotalScore += score;
            refreshScores();

            Toast.makeText(this,
                    "Tačno! +" + score + " bodova (korak " + openedSteps + ")",
                    Toast.LENGTH_LONG).show();

            setInputEnabled(false);
            handler.postDelayed(this::proceedToNextRound, 2000);
        } else {
            Toast.makeText(this, "Netačno, pokušajte ponovo!", Toast.LENGTH_SHORT).show();
        }
    }

    // ── Bonus faza ─────────────────────────────────────────────────────────────

    // Protivnik dobija 10 sekundi da osvoji 5 bonus bodova
    private void startOpponentBonusPhase() {
        bonusPhase = true;
        setInputEnabled(false);
        tvRunda.setText("Protivnik: 10 sekundi za 5 poena");
        startBonusCountdown(() -> {
            bonusPhase = false;
            // Solo/guest mod: simuliraj da protivnik ne pogađa (uvek 0 bonus)
            proceedToNextRound();
        });
    }

    // Simuliraj rezultat protivnika za rundu u kojoj on igra (solo mod)
    private void simulateOpponentRoundScore() {
        if (!isGuest && matchId != null) return; // samo u solo/guest modu
        // Protivnik nasumično pogađa na koraku 1-7 ili propušta
        int roll = new Random().nextInt(3); // 0=promaši, 1=pogodi na slučajnom koraku, 2=promaši
        if (roll == 1) {
            int stepGuessed = new Random().nextInt(7); // 0..6
            int score = Math.max(0, 20 - 2 * stepGuessed);
            oppTotalScore += score;
            refreshScores();
        }
    }

    // Ja dobijam 10 sekundi da osvoji 5 bonus bodova (protivnikova runda propala)
    private void startMyBonusPhase() {
        bonusPhase = true;
        setInputEnabled(true);
        tvRunda.setText("Bonus! 10 sekundi za 5 poena");
        etGuess.setText("");
        startBonusCountdown(() -> {
            bonusPhase = false;
            setInputEnabled(false);
            proceedToNextRound();
        });
    }

    private void startBonusCountdown(Runnable onFinished) {
        stopBonusTimer();
        pbTime.setMax(10);
        pbTime.setProgress(10);
        tvTimer.setText("10");

        bonusTimer = new CountDownTimer(10_000, 1_000) {
            @Override
            public void onTick(long ms) {
                int s = (int) (ms / 1000);
                pbTime.setProgress(s);
                tvTimer.setText(String.valueOf(s));
            }

            @Override
            public void onFinish() {
                pbTime.setProgress(0);
                tvTimer.setText("0");
                onFinished.run();
            }
        }.start();
    }

    private void handleBonusGuess() {
        String guess = etGuess.getText() != null
                ? etGuess.getText().toString().trim()
                : "";
        if (guess.isEmpty()) return;

        if (guess.equalsIgnoreCase(correctAnswer)) {
            stopBonusTimer();
            bonusPhase = false;
            setInputEnabled(false);

            myTotalScore += 5;
            refreshScores();

            Toast.makeText(this, "Tačno! +5 bonus poena!", Toast.LENGTH_SHORT).show();
            handler.postDelayed(this::proceedToNextRound, 1500);
        } else {
            Toast.makeText(this, "Netačno!", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopBonusTimer() {
        if (bonusTimer != null) {
            bonusTimer.cancel();
            bonusTimer = null;
        }
    }

    // ── Kraj runde / igre ──────────────────────────────────────────────────────

    private void markRemainingAsMissed() {
        for (Korak k : koraci) {
            if (k.getState() == KorakState.ZAKLJUCAN || k.getState() == KorakState.OTVOREN) {
                k.setState(KorakState.PROMASEN);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void proceedToNextRound() {
        if (currentRound < 2) {
            // Simuliraj protivnikov rezultat za rundu 2 (on "igra" tu rundu u pravom mul.)
            simulateOpponentRoundScore();
            handler.postDelayed(() -> startRound(2), 1000);
        } else {
            endGame();
        }
    }

    private void endGame() {
        setInputEnabled(false);
        btnFinish.setVisibility(View.VISIBLE);
        btnPredaj.setEnabled(false);
        tvRunda.setText("Kraj igre! Skor: " + myTotalScore);
        Toast.makeText(this,
                "Korak po korak završen! Ukupno: " + myTotalScore,
                Toast.LENGTH_LONG).show();
    }

    private void goToMojBroj() {
        stopRoundTimer();
        stopBonusTimer();
        Intent intent = new Intent(this, MojBrojActivity.class);
        intent.putExtra("isGuest",            isGuest);
        intent.putExtra("matchId",            matchId);
        intent.putExtra("myId",               myId);
        intent.putExtra("opponentId",         opponentId);
        intent.putExtra("isPlayer1",          isPlayer1);
        intent.putExtra("isFriendly",         isFriendly);
        intent.putExtra("totalMyScore",       prevMyScore  + myTotalScore);
        intent.putExtra("totalOpponentScore", prevOppScore + oppTotalScore);
        startActivity(intent);
        finish();
    }

    // ── UI pomoćnici ───────────────────────────────────────────────────────────

    private void setInputEnabled(boolean enabled) {
        etGuess.setEnabled(enabled);
        btnGuess.setEnabled(enabled);
    }

    private void refreshScores() {
        tvMyScore.setText(String.valueOf(myTotalScore));
        tvOppScore.setText(String.valueOf(oppTotalScore));
    }

    @Override
    protected void onDestroy() {
        stopRoundTimer();
        stopBonusTimer();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
