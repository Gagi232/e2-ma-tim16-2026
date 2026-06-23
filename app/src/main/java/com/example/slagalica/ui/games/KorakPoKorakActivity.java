package com.example.slagalica.ui.games;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.data.model.Korak;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.logic.LeagueLogic;
import com.example.slagalica.data.model.KorakState;
import com.example.slagalica.logic.KorakLogic;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import androidx.annotation.NonNull;

public class KorakPoKorakActivity extends AppCompatActivity {

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

    private List<Korak> koraci;
    private KorakAdapter adapter;
    private String correctAnswer;
    private int openedSteps = 0;
    private boolean roundActive = false;
    private boolean bonusPhase = false;

    private CountDownTimer roundTimer;
    private CountDownTimer bonusTimer;

    private int currentRound = 1;
    private int myTotalScore  = 0;
    private int oppTotalScore = 0;
    private int prevMyScore   = 0;
    private int prevOppScore  = 0;

    private boolean isPlayer1, isGuest;
    private String matchId, myId, opponentId;

    private final Random random = new Random();
    private final Handler handler = new Handler();

    // ── Multiplayer (NOVO) ──────────────────────────────────────────────────
    private DatabaseReference matchRef;
    private ValueEventListener roundListener;
    private boolean amIActiveThisRound;
    private int uiSetupRound = -1;
    private int timerStartedRound = -1;
    private boolean myGuessSubmittedThisRound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_korak_po_korak);

        isPlayer1     = getIntent().getBooleanExtra("isPlayer1", true);
        isGuest       = getIntent().getBooleanExtra("isGuest", false);
        matchId       = getIntent().getStringExtra("matchId");
        myId          = getIntent().getStringExtra("myId");
        isFriendly    = getIntent().getBooleanExtra("isFriendly", false);
        opponentId    = getIntent().getStringExtra("opponentId");
        prevMyScore   = getIntent().getIntExtra("totalMyScore", 0);
        prevOppScore  = getIntent().getIntExtra("totalOpponentScore", 0);

        bindViews();
        refreshScores();
        updateTopBar();

        if (isGuest || matchId == null) {
            startSoloRound(1);
        } else {
            matchRef = FirebaseDatabase.getInstance().getReference("activeMatches").child(matchId).child("korak");
            startMultiplayerRound(1);
        }
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
        btnPredaj.setOnClickListener(v -> handlePredaj());
        btnFinish.setOnClickListener(v -> goToMojBroj());
    }

    // ══════════════════════════ SOLO/GUEST (nepromenjeno) ══════════════════

    private void startSoloRound(int round) {
        currentRound = round;
        openedSteps  = 0;
        roundActive  = true;
        bonusPhase   = false;

        loadPuzzle(round);
        setupGridUI(round, true);

        openNextStep();
        startSoloRoundTimer();
    }

    private void startSoloRoundTimer() {
        stopRoundTimer();
        pbTime.setMax(70);
        pbTime.setProgress(70);
        tvTimer.setText("70");

        roundTimer = new CountDownTimer(70_000, 10_000) {
            @Override public void onTick(long ms) {
                int seconds = (int) (ms / 1000);
                pbTime.setProgress(seconds);
                tvTimer.setText(String.valueOf(seconds));
                if (roundActive) openNextStep();
            }
            @Override public void onFinish() {
                pbTime.setProgress(0);
                tvTimer.setText("0");
                if (roundActive) {
                    roundActive = false;
                    markRemainingAsMissed();
                    startSoloBonusPhase();
                }
            }
        }.start();
    }

    private void startSoloBonusPhase() {
        bonusPhase = true;
        setInputEnabled(false);
        tvRunda.setText("Bonus protivnika (simulacija): 10s");
        startBonusCountdown(() -> {
            bonusPhase = false;
            simulateOpponentRoundScore();
            proceedAfterRound();
        });
    }

    private void simulateOpponentRoundScore() {
        int roll = random.nextInt(3);
        if (roll == 1) {
            int stepGuessed = 1 + random.nextInt(7);
            oppTotalScore += KorakLogic.scoreForStep(stepGuessed);
            refreshScores();
        }
    }

    // ══════════════════════════ MULTIPLAYER (NOVO) ══════════════════════════

    private void startMultiplayerRound(int round) {
        currentRound = round;
        amIActiveThisRound = (round == 1) == isPlayer1;
        myGuessSubmittedThisRound = false;
        String roundKey = "round" + round;

        if (amIActiveThisRound) {
            Map<String, Object> data = new HashMap<>();
            data.put("phase", "active");
            data.put("startedAt", ServerValue.TIMESTAMP);
            data.put("activePlayerId", myId);
            data.put("otherPlayerId", opponentId);
            matchRef.child(roundKey).removeValue()
                    .addOnCompleteListener(t ->
                            matchRef.child(roundKey).setValue(data)
                                    .addOnFailureListener(e -> showWriteError("roundInit", e)));
        }
        listenToRound(roundKey);
    }

    private void listenToRound(String roundKey) {
        if (roundListener != null) matchRef.child(roundKey).removeEventListener(roundListener);
        roundListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                onRoundSnapshot(snap);
            }
            @Override
            public void onCancelled(DatabaseError error) {}
        };
        matchRef.child(roundKey).addValueEventListener(roundListener);
    }

    private void onRoundSnapshot(DataSnapshot snap) {
        if (!snap.exists()) return;
        Long startedAt = snap.child("startedAt").getValue(Long.class);
        if (startedAt == null) return; // jos nije inicijalizovano

        String phase = snap.child("phase").getValue(String.class);
        String activePlayerId = snap.child("activePlayerId").getValue(String.class);
        boolean iAmActive = myId.equals(activePlayerId);

        if (uiSetupRound != currentRound) {
            uiSetupRound = currentRound;
            loadPuzzle(currentRound);
            setupGridUI(currentRound, iAmActive);
        }

        if ("active".equals(phase)) {
            roundActive = true;
            bonusPhase = false;
            if (timerStartedRound != currentRound) {
                timerStartedRound = currentRound;
                startSyncedActiveTimer(startedAt, iAmActive);
            }
            setInputEnabled(iAmActive);
            tvRunda.setText("Runda " + currentRound + "/2 " + (iAmActive ? "(Tvoj red)" : "(Protivnik pogađa...)"));
        } else if ("bonus".equals(phase)) {
            stopRoundTimer();
            roundActive = false;
            if (!bonusPhase) {
                bonusPhase = true;
                Long bonusStartedAt = snap.child("bonusStartedAt").getValue(Long.class);
                if (bonusStartedAt != null) startSyncedBonusTimer(bonusStartedAt, !iAmActive);
            }
            if (iAmActive) {
                tvRunda.setText("Promašio si! Protivnik ima 10s za bonus.");
                setInputEnabled(false);
            } else {
                tvRunda.setText("Bonus! 10 sekundi za " + KorakLogic.BONUS_SCORE + " poena!");
                setInputEnabled(true);
            }
        } else if ("done".equals(phase)) {
            stopRoundTimer();
            stopBonusTimer();
            if (roundActive || bonusPhase) {
                roundActive = false;
                bonusPhase = false;
                applyRoundScoresAndProceed(snap);
            }
        }
    }

    private void startSyncedActiveTimer(long startedAt, boolean iAmActive) {
        stopRoundTimer();
        long elapsed = System.currentTimeMillis() - startedAt;
        long remaining = KorakLogic.ROUND_TIME_MS - elapsed;
        if (remaining < 0) remaining = 0;

        // Catch-up: odmah prikazi korake koji su vec "trebali" biti otvoreni
        openedSteps = KorakLogic.openedStepsFromElapsed(Math.max(0, elapsed));
        revealStepsUpTo(openedSteps);

        pbTime.setMax(70);
        pbTime.setProgress((int) (remaining / 1000));
        tvTimer.setText(String.valueOf(remaining / 1000));

        roundTimer = new CountDownTimer(remaining, 500) {
            @Override public void onTick(long ms) {
                long el = System.currentTimeMillis() - startedAt;
                int steps = KorakLogic.openedStepsFromElapsed(el);
                if (steps != openedSteps) {
                    openedSteps = steps;
                    revealStepsUpTo(openedSteps);
                }
                int secLeft = (int) (ms / 1000);
                pbTime.setProgress(secLeft);
                tvTimer.setText(String.valueOf(secLeft));
            }
            @Override public void onFinish() {
                pbTime.setProgress(0);
                tvTimer.setText("0");
                markRemainingAsMissed();
                // SAMO aktivni igrac (ciji je tajmer ovo) pise tranziciju u bazu,
                // da ne dodje do duplog pisanja od oba klijenta istovremeno.
                if (iAmActive && !myGuessSubmittedThisRound) {
                    transitionToBonusPhase();
                }
            }
        }.start();
    }

    private void startSyncedBonusTimer(long bonusStartedAt, boolean iAmBonusPlayer) {
        stopBonusTimer();
        long elapsed = System.currentTimeMillis() - bonusStartedAt;
        long remaining = KorakLogic.BONUS_TIME_MS - elapsed;
        if (remaining < 0) remaining = 0;

        pbTime.setMax(10);
        pbTime.setProgress((int) (remaining / 1000));
        tvTimer.setText(String.valueOf(remaining / 1000));

        bonusTimer = new CountDownTimer(remaining, 500) {
            @Override public void onTick(long ms) {
                int s = (int) (ms / 1000);
                pbTime.setProgress(s);
                tvTimer.setText(String.valueOf(s));
            }
            @Override public void onFinish() {
                pbTime.setProgress(0);
                tvTimer.setText("0");
                // SAMO bonus igrac pise "done" ako nije vec pogodio
                if (iAmBonusPlayer && !myGuessSubmittedThisRound) {
                    transitionToDone(false);
                }
            }
        }.start();
    }

    private void transitionToBonusPhase() {
        String roundKey = "round" + currentRound;
        Map<String, Object> update = new HashMap<>();
        update.put("phase", "bonus");
        update.put("bonusStartedAt", ServerValue.TIMESTAMP);
        matchRef.child(roundKey).updateChildren(update)
                .addOnFailureListener(e -> showWriteError("phase=bonus", e));
    }

    private void transitionToDone(boolean bonusCorrect) {
        String roundKey = "round" + currentRound;
        Map<String, Object> update = new HashMap<>();
        update.put("phase", "done");
        if (bonusCorrect) update.put("bonusScore", KorakLogic.BONUS_SCORE);
        matchRef.child(roundKey).updateChildren(update)
                .addOnFailureListener(e -> showWriteError("phase=done", e));
    }

    private void applyRoundScoresAndProceed(DataSnapshot snap) {
        Integer activeScore = snap.child("activeScore").getValue(Integer.class);
        Integer bonusScore  = snap.child("bonusScore").getValue(Integer.class);
        String activePlayerId = snap.child("activePlayerId").getValue(String.class);
        boolean iWasActive = myId.equals(activePlayerId);

        int myPts  = iWasActive ? (activeScore != null ? activeScore : 0) : (bonusScore != null ? bonusScore : 0);
        int oppPts = iWasActive ? (bonusScore != null ? bonusScore : 0) : (activeScore != null ? activeScore : 0);

        myTotalScore  += myPts;
        oppTotalScore += oppPts;
        refreshScores();

        if (roundListener != null) matchRef.child("round" + currentRound).removeEventListener(roundListener);
        roundListener = null;

        proceedAfterRound();
    }

    // ══════════════════════════ Zajedničko ══════════════════════════════════

    private void loadPuzzle(int round) {
        int puzzleIndex = (round - 1) % PUZZLES.length;
        String[] puzzle = PUZZLES[puzzleIndex];
        correctAnswer = puzzle[0];

        koraci = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            koraci.add(new Korak(i, puzzle[i], KorakState.ZAKLJUCAN));
        }
        adapter = new KorakAdapter(koraci);
        rvKoraci.setAdapter(adapter);
    }

    private void setupGridUI(int round, boolean inputEnabledNow) {
        openedSteps = 0;
        roundActive = true;
        bonusPhase = false;
        myGuessSubmittedThisRound = false;
        tvRunda.setText("Runda " + round + "/2");
        etGuess.setText("");
        refreshScores();
        setInputEnabled(inputEnabledNow);
        revealStepsUpTo(1); // prvi korak odmah otvoren
    }

    private void revealStepsUpTo(int count) {
        for (int i = 0; i < koraci.size(); i++) {
            if (i < count && koraci.get(i).getState() == KorakState.ZAKLJUCAN) {
                koraci.get(i).setState(KorakState.OTVOREN);
                adapter.notifyItemChanged(i);
            }
        }
        openedSteps = count;
    }

    private void openNextStep() {
        if (openedSteps < koraci.size()) {
            koraci.get(openedSteps).setState(KorakState.OTVOREN);
            adapter.notifyItemChanged(openedSteps);
            openedSteps++;
        }
    }

    private void handleGuess() {
        if (matchRef != null) {
            handleMultiplayerGuess();
        } else {
            handleSoloGuess();
        }
    }

    private void handleSoloGuess() {
        if (bonusPhase) { handleSoloBonusGuess(); return; }
        if (!roundActive) return;

        String guess = etGuess.getText() != null ? etGuess.getText().toString().trim() : "";
        if (guess.isEmpty()) { Toast.makeText(this, "Unesite odgovor!", Toast.LENGTH_SHORT).show(); return; }

        if (guess.equalsIgnoreCase(correctAnswer)) {
            int score = KorakLogic.scoreForStep(openedSteps);
            stopRoundTimer();
            roundActive = false;
            markStepHit();
            myTotalScore += score;
            refreshScores();
            Toast.makeText(this, "Tačno! +" + score + " bodova", Toast.LENGTH_LONG).show();
            setInputEnabled(false);
            handler.postDelayed(this::proceedAfterRound, 2000);
        } else {
            Toast.makeText(this, "Netačno, pokušajte ponovo!", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSoloBonusGuess() {
        String guess = etGuess.getText() != null ? etGuess.getText().toString().trim() : "";
        if (guess.isEmpty()) return;
        if (guess.equalsIgnoreCase(correctAnswer)) {
            stopBonusTimer();
            bonusPhase = false;
            setInputEnabled(false);
            myTotalScore += KorakLogic.BONUS_SCORE;
            refreshScores();
            Toast.makeText(this, "Tačno! +5 bonus poena!", Toast.LENGTH_SHORT).show();
            handler.postDelayed(this::proceedAfterRound, 1500);
        } else {
            Toast.makeText(this, "Netačno!", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleMultiplayerGuess() {
        String guess = etGuess.getText() != null ? etGuess.getText().toString().trim() : "";
        if (guess.isEmpty()) { Toast.makeText(this, "Unesite odgovor!", Toast.LENGTH_SHORT).show(); return; }
        if (myGuessSubmittedThisRound) return;

        boolean correct = guess.equalsIgnoreCase(correctAnswer);
        String roundKey = "round" + currentRound;

        if (roundActive) {
            // Aktivna faza - samo aktivni igrac sme da pogadja
            if (!correct) { Toast.makeText(this, "Netačno, pokušajte ponovo!", Toast.LENGTH_SHORT).show(); return; }
            myGuessSubmittedThisRound = true;
            stopRoundTimer();
            markStepHit();
            int score = KorakLogic.scoreForStep(openedSteps);
            setInputEnabled(false);
            Map<String, Object> update = new HashMap<>();
            update.put("phase", "done");
            update.put("activeScore", score);
            matchRef.child(roundKey).updateChildren(update)
                    .addOnFailureListener(e -> showWriteError("activeGuess", e));
        } else if (bonusPhase) {
            myGuessSubmittedThisRound = true;
            stopBonusTimer();
            setInputEnabled(false);
            if (correct) {
                Toast.makeText(this, "Tačno! +5 bonus poena!", Toast.LENGTH_SHORT).show();
                transitionToDone(true);
            } else {
                Toast.makeText(this, "Netačno!", Toast.LENGTH_SHORT).show();
                transitionToDone(false);
            }
        }
        etGuess.setText("");
    }

    private void handlePredaj() {
        if (matchRef != null) {
            if (roundActive && !myGuessSubmittedThisRound) {
                stopRoundTimer();
                markRemainingAsMissed();
                myGuessSubmittedThisRound = true; // ja necu vise pogadjati
                String roundKey = "round" + currentRound;
                Map<String, Object> update = new HashMap<>();
                update.put("phase", "bonus");
                update.put("bonusStartedAt", ServerValue.TIMESTAMP);
                matchRef.child(roundKey).updateChildren(update)
                        .addOnFailureListener(e -> showWriteError("predaj", e));
            }
        } else {
            if (!roundActive || bonusPhase) return;
            stopRoundTimer();
            roundActive = false;
            markRemainingAsMissed();
            startSoloBonusPhase();
        }
    }

    private void markStepHit() {
        int hitIndex = openedSteps - 1;
        if (hitIndex >= 0 && hitIndex < koraci.size()) {
            koraci.get(hitIndex).setState(KorakState.POGODJEN);
            adapter.notifyItemChanged(hitIndex);
        }
    }

    private void markRemainingAsMissed() {
        for (Korak k : koraci) {
            if (k.getState() == KorakState.ZAKLJUCAN || k.getState() == KorakState.OTVOREN) {
                k.setState(KorakState.PROMASEN);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void startBonusCountdown(Runnable onFinished) {
        stopBonusTimer();
        pbTime.setMax(10);
        pbTime.setProgress(10);
        tvTimer.setText("10");
        bonusTimer = new CountDownTimer(10_000, 1_000) {
            @Override public void onTick(long ms) {
                int s = (int) (ms / 1000);
                pbTime.setProgress(s);
                tvTimer.setText(String.valueOf(s));
            }
            @Override public void onFinish() {
                pbTime.setProgress(0);
                tvTimer.setText("0");
                onFinished.run();
            }
        }.start();
    }

    private void proceedAfterRound() {
        if (currentRound < 2) {
            int next = currentRound + 1;
            handler.postDelayed(() -> {
                if (matchRef != null) startMultiplayerRound(next);
                else startSoloRound(next);
            }, 1500);
        } else {
            endGame();
        }
    }

    private void endGame() {
        setInputEnabled(false);
        btnFinish.setVisibility(View.VISIBLE);
        btnPredaj.setEnabled(false);
        tvRunda.setText("Kraj igre! Skor: " + myTotalScore);
        Toast.makeText(this, "Korak po korak završen! Ukupno: " + myTotalScore, Toast.LENGTH_LONG).show();
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

    private void setInputEnabled(boolean enabled) {
        etGuess.setEnabled(enabled);
        btnGuess.setEnabled(enabled);
    }

    private void refreshScores() {
        tvMyScore.setText(String.valueOf(prevMyScore + myTotalScore));
        tvOppScore.setText(String.valueOf(prevOppScore + oppTotalScore));
    }

    private void updateTopBar() {
        TextView tvTokens = findViewById(R.id.tvTokens);
        TextView tvStars  = findViewById(R.id.tvStars);
        TextView tvLeague = findViewById(R.id.tvLeague);

        if (isGuest) {
            if (tvTokens != null) tvTokens.setText("0");
            if (tvStars  != null) tvStars.setText("0");
            if (tvLeague != null) tvLeague.setText("🏆");
            return;
        }

        new UserRepository().getCurrentUser(new UserRepository.Callback<User>() {
            @Override
            public void onSuccess(User user) {
                if (user != null) {
                    if (tvTokens != null) tvTokens.setText(String.valueOf(user.getTokens()));
                    if (tvStars  != null) tvStars.setText(String.valueOf(user.getStars()));
                    int league = LeagueLogic.calculateLeague(user.getStars());
                    if (tvLeague != null) {
                        tvLeague.setText(LeagueLogic.getLeagueIcon(league));
                        tvLeague.setOnClickListener(v -> showLeagueDialog());
                    }
                }
            }
            @Override public void onError(Exception e) {}
        });
    }

    private void showLeagueDialog() {
        String[] leagues = {"🏆 Liga 0", "📚 Početnička Liga", "🧠 Školska Liga", "🏛️ Akademska Liga", "👑 Genijalac Liga"};
        new AlertDialog.Builder(this).setTitle("Lige").setItems(leagues, null).show();
    }


    private void stopRoundTimer() {
        if (roundTimer != null) { roundTimer.cancel(); roundTimer = null; }
    }

    private void stopBonusTimer() {
        if (bonusTimer != null) { bonusTimer.cancel(); bonusTimer = null; }
    }

    private void showWriteError(String what, Exception e) {
        Toast.makeText(this, "Write error (" + what + "): " + e.getMessage(), Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        stopRoundTimer();
        stopBonusTimer();
        handler.removeCallbacksAndMessages(null);
        if (roundListener != null && matchRef != null) {
            matchRef.child("round" + currentRound).removeEventListener(roundListener);
        }
        super.onDestroy();
    }
}