package com.example.slagalica.ui.games;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.MainActivity;
import com.example.slagalica.R;
import com.example.slagalica.data.repository.StatsRepository;
import com.example.slagalica.logic.MojBrojLogic;
import com.example.slagalica.ui.main.GuestActivity;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.logic.LeagueLogic;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.logic.LeagueLogic;
import com.example.slagalica.ui.main.ResultsActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MojBrojActivity extends AppCompatActivity {

    // ── UI ────────────────────────────────────────────────────────────────────
    private TextView tvTrazeniBroj, tvTimer, tvMyScore, tvOppScore, tvRunda, tvStatus;
    private TextView[] tvBrojevi = new TextView[6];
    private MaterialButton[] btnBrojevi = new MaterialButton[6];
    private TextInputEditText etIzraz;
    private ProgressBar pbTime;
    private MaterialButton btnNext, btnStop, btnPotvrdi, btnPredaj;

    // ── Stanje ────────────────────────────────────────────────────────────────
    // Faze: 0=čeka STOP1(target), 1=čeka STOP2(brojevi), 2=igra, 3=završeno
    private int faza = 0;
    private int currentRound = 1;

    private int targetNumber = 0;
    private int[] shownNumbers = new int[6];
    private final StringBuilder izraz = new StringBuilder();

    private int myResult1  = Integer.MIN_VALUE;
    private int myResult2  = Integer.MIN_VALUE;
    private int oppResult1 = Integer.MIN_VALUE;
    private int oppResult2 = Integer.MIN_VALUE;

    private int myTotalScore  = 0;
    private int oppTotalScore = 0;
    private int prevMyScore   = 0;
    private int prevOppScore  = 0;

    // ── Match podaci ──────────────────────────────────────────────────────────
    private String matchId, myId, opponentId;
    private boolean isPlayer1, isGuest, isFriendly;

    // ── Multiplayer state ─────────────────────────────────────────────────────
    private DatabaseReference matchRef = null;
    // Da li je ovaj igrac "inicijator" runde (piše target/brojeve u Firebase)
    private boolean amIInitiatorThisRound;
    private boolean myResultSubmittedThisRound = false;
    private boolean roundScoresApplied = false;
    private ValueEventListener roundListener = null;
    private int listenerRound = -1;

    // ── Timeri ─────────────────────────────────────────────────────────────────
    private CountDownTimer roundTimer    = null;
    private CountDownTimer autoStopTimer = null;
    private final Handler handler = new Handler();

    // ── Shake senzor ──────────────────────────────────────────────────────────
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastShakeTime = 0;
    private static final float SHAKE_THRESHOLD = 2.7f;
    private static final int SHAKE_COOLDOWN_MS = 600;

    private final Random random = new Random();

    private final SensorEventListener shakeListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            float x = event.values[0], y = event.values[1], z = event.values[2];
            float g = (float) Math.sqrt(x*x + y*y + z*z) / SensorManager.GRAVITY_EARTH;
            if (g > SHAKE_THRESHOLD) {
                long now = System.currentTimeMillis();
                if (now - lastShakeTime < SHAKE_COOLDOWN_MS) return;
                lastShakeTime = now;
                if (faza == 0 || faza == 1) runOnUiThread(MojBrojActivity.this::handleStop);
            }
        }
        @Override public void onAccuracyChanged(Sensor s, int a) {}
    };

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moj_broj);

        isGuest    = getIntent().getBooleanExtra("isGuest", false);
        matchId    = getIntent().getStringExtra("matchId");
        myId       = getIntent().getStringExtra("myId");
        isFriendly = getIntent().getBooleanExtra("isFriendly", false);
        opponentId = getIntent().getStringExtra("opponentId");
        isPlayer1  = getIntent().getBooleanExtra("isPlayer1", true);
        prevMyScore  = getIntent().getIntExtra("totalMyScore", 0);
        prevOppScore = getIntent().getIntExtra("totalOpponentScore", 0);

        if (!isGuest && matchId != null) {
            matchRef = FirebaseDatabase.getInstance()
                    .getReference("activeMatches").child(matchId).child("mojBroj");
        }

        listenForForfeit();

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null)
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        bindViews();
        refreshScores();
        updateTopBar();
        setupButtons();
        startRound(1);
    }

    // Nova polja
    private ValueEventListener forfeitListener;
    private DatabaseReference matchInfoStatusRef;

    private void listenForForfeit() {
        if (matchId == null || isGuest) return;
        matchInfoStatusRef = FirebaseDatabase.getInstance()
                .getReference("activeMatches").child(matchId).child("info").child("status");
        forfeitListener = new ValueEventListener() {
            @Override
            public void onDataChange(@androidx.annotation.NonNull DataSnapshot snap) {
                String status = snap.getValue(String.class);
                if (status != null && status.equals("forfeit_" + opponentId)) {
                    handleOpponentForfeit();
                }
            }
            @Override public void onCancelled(@androidx.annotation.NonNull DatabaseError error) {}
        };
        matchInfoStatusRef.addValueEventListener(forfeitListener);
    }

    private void removeForfeitListener() {
        if (forfeitListener != null && matchInfoStatusRef != null) {
            matchInfoStatusRef.removeEventListener(forfeitListener);
        }
        forfeitListener = null;
    }

    @Override protected void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometer != null)
            sensorManager.registerListener(shakeListener, accelerometer, SensorManager.SENSOR_DELAY_UI);
    }

    @Override protected void onPause() {
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(shakeListener);
    }

    // ── Bind ──────────────────────────────────────────────────────────────────

    private void bindViews() {
        tvTrazeniBroj = findViewById(R.id.tvTrazeniBroj);
        tvTimer       = findViewById(R.id.tvTimer);
        tvMyScore     = findViewById(R.id.tvMyScore);
        tvOppScore    = findViewById(R.id.tvOpponentScore);
        tvRunda       = findViewById(R.id.tvRunda);
        etIzraz       = findViewById(R.id.etIzraz);
        pbTime        = findViewById(R.id.pbTime);
        btnNext       = findViewById(R.id.btnNext);
        btnStop       = findViewById(R.id.btnStop);
        btnPotvrdi    = findViewById(R.id.btnPotvrdi);
        btnPredaj     = findViewById(R.id.btnPredaj);

        // tvStatus je opcionalni TextView za poruke tipa "Čekaj protivnika..."

        tvBrojevi[0] = findViewById(R.id.tvBroj1); tvBrojevi[1] = findViewById(R.id.tvBroj2);
        tvBrojevi[2] = findViewById(R.id.tvBroj3); tvBrojevi[3] = findViewById(R.id.tvBroj4);
        tvBrojevi[4] = findViewById(R.id.tvBroj5); tvBrojevi[5] = findViewById(R.id.tvBroj6);

        btnBrojevi[0] = findViewById(R.id.btnB1); btnBrojevi[1] = findViewById(R.id.btnB2);
        btnBrojevi[2] = findViewById(R.id.btnB3); btnBrojevi[3] = findViewById(R.id.btnB4);
        btnBrojevi[4] = findViewById(R.id.btnB5); btnBrojevi[5] = findViewById(R.id.btnB6);
    }

    private void setupButtons() {
        for (int i = 0; i < btnBrojevi.length; i++) {
            final int idx = i;
            btnBrojevi[i].setOnClickListener(v -> {
                if (faza != 2) return;
                String val = tvBrojevi[idx].getText().toString();
                if (!"?".equals(val)) addToExpression(val);
            });
        }

        findViewById(R.id.btnLeftParen).setOnClickListener(v  -> addOperator("("));
        findViewById(R.id.btnRightParen).setOnClickListener(v -> addOperator(")"));
        findViewById(R.id.btnPlus).setOnClickListener(v       -> addOperator("+"));
        findViewById(R.id.btnMinus).setOnClickListener(v      -> addOperator("-"));
        findViewById(R.id.btnMul).setOnClickListener(v        -> addOperator("x"));
        findViewById(R.id.btnDiv).setOnClickListener(v        -> addOperator("/"));

        findViewById(R.id.btnDelete).setOnClickListener(v -> {
            if (faza != 2 || izraz.length() == 0) return;
            izraz.deleteCharAt(izraz.length() - 1);
            refreshExpression();
        });
        findViewById(R.id.btnReset).setOnClickListener(v -> {
            if (faza != 2) return;
            izraz.setLength(0);
            refreshExpression();
        });

        btnStop.setOnClickListener(v -> handleStop());
        btnPotvrdi.setOnClickListener(v -> handlePotvrdi());
        btnPredaj.setOnClickListener(v -> forfeit());

        btnNext.setEnabled(false);
        btnNext.setOnClickListener(v -> goToResults());
    }

    // ── Runde ─────────────────────────────────────────────────────────────────

    private void startRound(int round) {
        currentRound = round;
        // Runda 1: player1 je inicijator; Runda 2: player2 je inicijator
        amIInitiatorThisRound = isGuest || matchRef == null || (round == 1) == isPlayer1;
        myResultSubmittedThisRound = false;
        roundScoresApplied = false;

        faza = 0;
        izraz.setLength(0);
        refreshExpression();
        resetNumberDisplay();

        tvTrazeniBroj.setText("?");
        tvTimer.setText("60");
        tvRunda.setText("Runda " + round + "/2");
        pbTime.setVisibility(View.INVISIBLE);
        btnStop.setEnabled(true);
        setInputEnabled(false);
        refreshScores();

        if (matchRef != null) {
            // Ukloni stari listener pre nego što postavimo novi
            removeRoundListener();
            listenToRound(round);

            if (amIInitiatorThisRound) {
                setStatus("Pritisni STOP za traženi broj...");
            } else {
                setStatus("Čekaj protivnika da pritisne STOP...");
                btnStop.setEnabled(false);
            }
        } else {
            // Solo/guest
            setStatus("");
        }
    }

    // ── Firebase listener ─────────────────────────────────────────────────────

    private void listenToRound(int round) {
        listenerRound = round;
        roundListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                onRoundSnapshot(snap, round);
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        matchRef.child("round" + round).addValueEventListener(roundListener);
    }

    private void removeRoundListener() {
        if (roundListener != null && matchRef != null && listenerRound >= 1) {
            matchRef.child("round" + listenerRound).removeEventListener(roundListener);
            roundListener = null;
        }
    }

    private void onRoundSnapshot(DataSnapshot snap, int round) {
        if (!snap.exists()) return;

        String phase = snap.child("phase").getValue(String.class);
        if (phase == null) return;

        switch (phase) {

            case "waitNumbers": {
                // Target je postavljen, oba igrača ga vide
                Integer t = snap.child("target").getValue(Integer.class);
                if (t == null) return;
                targetNumber = t;
                tvTrazeniBroj.setText(String.valueOf(targetNumber));
                faza = 1;
                if (amIInitiatorThisRound) {
                    setStatus("Pritisni STOP za brojeve...");
                    btnStop.setEnabled(true);
                } else {
                    setStatus("Protivnik bira brojeve...");
                    btnStop.setEnabled(false);
                    // Auto-stop: ako inicijator ne reaguje, listener će primiti "playing"
                }
                break;
            }

            case "playing": {
                // Brojevi su postavljeni, tajmer je startovan
                Long startedAt = snap.child("startedAt").getValue(Long.class);
                if (startedAt == null) return;

                // Učitaj brojeve koje je inicijator generisao
                DataSnapshot numsSnap = snap.child("numbers");
                if (numsSnap.exists()) {
                    int idx = 0;
                    for (DataSnapshot n : numsSnap.getChildren()) {
                        if (idx < 6) {
                            Integer v = n.getValue(Integer.class);
                            shownNumbers[idx] = (v != null) ? v : 0;
                            String s = String.valueOf(shownNumbers[idx]);
                            tvBrojevi[idx].setText(s);
                            btnBrojevi[idx].setText(s);
                            idx++;
                        }
                    }
                }

                faza = 2;
                btnStop.setEnabled(false);
                setInputEnabled(true);
                setStatus("Igra!");
                pbTime.setVisibility(View.VISIBLE);

                // Startuj sinhronizovani tajmer
                startSyncedRoundTimer(startedAt);
                break;
            }

            case "done": {
                // Oba rezultata su upisana (ili je tajmer istekao)
                if (roundScoresApplied) break;
                Integer res1 = snap.child("result").child(isPlayer1 ? "player1" : "player2").getValue(Integer.class);
                Integer res2 = snap.child("result").child(isPlayer1 ? "player2" : "player1").getValue(Integer.class);

                int myRes  = (res1 == -999999) ? Integer.MIN_VALUE : res1;
                int oppRes = (res2 == -999999) ? Integer.MIN_VALUE : res2;

                // Čekaj da oba rezultata stignu
                if (res1 == null || res2 == null) break;

                roundScoresApplied = true;
                stopRoundTimer();
                stopAutoStop();
                setInputEnabled(false);
                faza = 3;

                if (round == 1) { myResult1 = myRes; oppResult1 = oppRes; }
                else            { myResult2 = myRes; oppResult2 = oppRes; }

                boolean myRound = amIInitiatorThisRound;
                int myPts  = MojBrojLogic.calcScore(myRes,  oppRes, targetNumber, myRound);
                int oppPts = MojBrojLogic.calcScore(oppRes, myRes,  targetNumber, !myRound);

                myTotalScore  += myPts;
                oppTotalScore += oppPts;
                refreshScores();

                Toast.makeText(this,
                        "Ti=" + (myRes == Integer.MIN_VALUE ? "ništa" : myRes)
                                + " Prot=" + (oppRes == Integer.MIN_VALUE ? "ništa" : oppRes)
                                + " Traženi=" + targetNumber
                                + " → +" + myPts,
                        Toast.LENGTH_LONG).show();

                removeRoundListener();
                handler.postDelayed(() -> proceedAfterRound(round), 2500);
                break;
            }
        }
    }

    // ── STOP ──────────────────────────────────────────────────────────────────

    private void handleStop() {
        if (matchRef == null || isGuest) {
            handleStopSolo();
            return;
        }

        if (faza == 0 && amIInitiatorThisRound) {
            // Generiši traženi broj i upiši u Firebase
            targetNumber = 100 + random.nextInt(900);
            Map<String, Object> data = new HashMap<>();
            data.put("phase", "waitNumbers");
            data.put("target", targetNumber);
            matchRef.child("round" + currentRound).setValue(data);
            // Lokalno ažuriranje — listener će uraditi ostalo
            faza = 1;
            tvTrazeniBroj.setText(String.valueOf(targetNumber));
            setStatus("Pritisni STOP za brojeve...");
            beginAutoStop(); // auto-stop za 5s

        } else if (faza == 1 && amIInitiatorThisRound) {
            stopAutoStop();
            revealAndWriteNumbers();
        }
    }

    private void handleStopSolo() {
        if (faza == 0) {
            targetNumber = 100 + random.nextInt(900);
            tvTrazeniBroj.setText(String.valueOf(targetNumber));
            faza = 1;
            beginAutoStop();
        } else if (faza == 1) {
            stopAutoStop();
            revealNumbersSolo();
            faza = 2;
            setInputEnabled(true);
            beginRoundTimerSolo();
        }
    }

    /** Inicijator generira 6 brojeva, upisuje ih u Firebase zajedno sa startedAt */
    private void revealAndWriteNumbers() {
        List<Integer> list = generateNumbers();
        for (int i = 0; i < 6; i++) {
            shownNumbers[i] = list.get(i);
            String s = String.valueOf(shownNumbers[i]);
            tvBrojevi[i].setText(s);
            btnBrojevi[i].setText(s);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("phase", "playing");
        data.put("startedAt", ServerValue.TIMESTAMP);
        List<Integer> numList = new ArrayList<>();
        for (int n : shownNumbers) numList.add(n);
        data.put("numbers", numList);
        matchRef.child("round" + currentRound).updateChildren(data);

        faza = 2;
        setInputEnabled(true);
        btnStop.setEnabled(false);
        pbTime.setVisibility(View.VISIBLE);
        setStatus("Igra!");
        // Timer će startovati kada listener dobije "playing" sa startedAt
    }

    private List<Integer> generateNumbers() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 4; i++) list.add(1 + random.nextInt(9));
        int[] tens  = {10, 15, 20};
        int[] large = {25, 50, 75, 100};
        list.add(tens[random.nextInt(tens.length)]);
        list.add(large[random.nextInt(large.length)]);
        Collections.shuffle(list, random);
        return list;
    }

    private void revealNumbersSolo() {
        List<Integer> list = generateNumbers();
        for (int i = 0; i < 6; i++) {
            shownNumbers[i] = list.get(i);
            String s = String.valueOf(shownNumbers[i]);
            tvBrojevi[i].setText(s);
            btnBrojevi[i].setText(s);
        }
    }

    // ── Potvrdi ───────────────────────────────────────────────────────────────

    private void handlePotvrdi() {
        if (faza != 2) return;
        String expr = izraz.toString().trim();
        if (expr.isEmpty()) { Toast.makeText(this, "Unesi izraz!", Toast.LENGTH_SHORT).show(); return; }
        if (myResultSubmittedThisRound) return;

        String evalExpr = expr.replace("x", "*");
        int result = MojBrojLogic.evaluate(evalExpr);
        if (result == Integer.MIN_VALUE) {
            Toast.makeText(this, "Neispravan izraz!", Toast.LENGTH_SHORT).show();
            return;
        }

        stopRoundTimer();
        faza = 3;
        myResultSubmittedThisRound = true;
        setInputEnabled(false);

        boolean exact = (result == targetNumber);
        Toast.makeText(this, exact ? "Tačno! " + result : "Rezultat: " + result, Toast.LENGTH_SHORT).show();

        submitMyResult(result);
    }

    private void submitMyResult(int result) {
        if (matchRef != null && myId != null) {
            String key = isPlayer1 ? "player1" : "player2";
            // Sentinel za "ništa"
            int firebaseResult = (result == Integer.MIN_VALUE) ? -999999 : result;
            matchRef.child("round" + currentRound).child("result").child(key)
                    .setValue(firebaseResult)
                    .addOnSuccessListener(v -> waitForBothResults())  // <-- promenjeno
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Greška pri upisu!", Toast.LENGTH_SHORT).show());
        } else {
            if (currentRound == 1) myResult1 = result;
            else myResult2 = result;
            simulateOpponentSolo();
        }
    }

    // Oba igrača čekaju da se oba rezultata pojave, pa ko prvi vidi — piše done
    private void waitForBothResults() {
        matchRef.child("round" + currentRound).child("result")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snap) {
                        boolean p1Done = snap.child("player1").exists();
                        boolean p2Done = snap.child("player2").exists();
                        if (p1Done && p2Done) {
                            matchRef.child("round" + currentRound).child("phase").setValue("done");
                        } else {
                            // Čekaj 1s pa provjeri ponovo
                            handler.postDelayed(() -> waitForBothResults(), 1000);
                        }
                    }
                    @Override public void onCancelled(DatabaseError e) {}
                });
    }

    /**
     * Nakon što svaki igrač upiše rezultat, proveravamo da li su oba upisana.
     * Ako jesu, inicijator piše "done" fazu — to sprečava race condition.
     */
    private void checkBothResultsSubmitted() {
        matchRef.child("round" + currentRound).child("result")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snap) {
                        boolean p1Done = snap.child("player1").exists();
                        boolean p2Done = snap.child("player2").exists();
                        if (p1Done && p2Done) {
                            // Ko god vidi oba, piše done — Firebase updateChildren je idempotent
                            matchRef.child("round" + currentRound).child("phase").setValue("done");
                        }
                        // Ako nije još oba, čekamo — listener na roundu će okiditi kad drugi upiše
                        // i on će pozvati checkBothResultsSubmitted
                    }
                    @Override public void onCancelled(DatabaseError e) {}
                });
    }

    // ── Sinhronizovani tajmer ─────────────────────────────────────────────────

    private void startSyncedRoundTimer(long startedAt) {
        if (faza == 3) return; // već završeno
        stopRoundTimer();
        long elapsed   = System.currentTimeMillis() - startedAt;
        long remaining = 60_000 - elapsed;
        if (remaining < 0) remaining = 0;

        pbTime.setMax(60);
        pbTime.setProgress((int)(remaining / 1000));
        tvTimer.setText(String.valueOf(remaining / 1000));

        roundTimer = new CountDownTimer(remaining, 500) {
            @Override public void onTick(long ms) {
                int s = (int)(ms / 1000);
                pbTime.setProgress(s);
                tvTimer.setText(String.valueOf(s));
            }
            @Override public void onFinish() {
                pbTime.setProgress(0);
                tvTimer.setText("0");
                onRoundTimerExpired();
            }
        }.start();
    }

    private void onRoundTimerExpired() {
        if (faza == 3 || myResultSubmittedThisRound) return; // već smo submittali
        faza = 3;
        setInputEnabled(false);
        myResultSubmittedThisRound = true;
        // Upiši Integer.MIN_VALUE kao signal "ništa nije uneto"
        // Firebase ne podržava MIN_VALUE direktno, koristimo sentinel -999999
        submitMyResult(Integer.MIN_VALUE);
    }

    private void beginRoundTimerSolo() {
        stopRoundTimer();
        pbTime.setVisibility(View.VISIBLE);
        pbTime.setMax(60);
        pbTime.setProgress(60);
        roundTimer = new CountDownTimer(60_000, 1_000) {
            @Override public void onTick(long ms) {
                int s = (int)(ms/1000);
                pbTime.setProgress(s); tvTimer.setText(String.valueOf(s));
            }
            @Override public void onFinish() {
                pbTime.setProgress(0); tvTimer.setText("0");
                faza = 3;
                if (currentRound == 1) myResult1 = Integer.MIN_VALUE;
                else myResult2 = Integer.MIN_VALUE;
                simulateOpponentSolo();
            }
        }.start();
    }

    private void beginAutoStop() {
        stopAutoStop();
        autoStopTimer = new CountDownTimer(5_000, 1_000) {
            @Override public void onTick(long ms) {}
            @Override public void onFinish() {
                if (matchRef != null && !isGuest) {
                    if (faza == 1) revealAndWriteNumbers();
                } else {
                    if (faza == 1) { revealNumbersSolo(); faza = 2; setInputEnabled(true); beginRoundTimerSolo(); }
                }
            }
        }.start();
    }

    // ── Solo simulacija ───────────────────────────────────────────────────────

    private void simulateOpponentSolo() {
        int oppResult = random.nextBoolean() ? targetNumber : targetNumber + random.nextInt(50) - 25;
        if (currentRound == 1) oppResult1 = oppResult;
        else oppResult2 = oppResult;
        finishRoundSolo();
    }

    private void finishRoundSolo() {
        stopRoundTimer();
        int myRes  = (currentRound == 1) ? myResult1 : myResult2;
        int oppRes = (currentRound == 1) ? oppResult1 : oppResult2;

        int myPts  = MojBrojLogic.calcScore(myRes,  oppRes, targetNumber, amIInitiatorThisRound);
        int oppPts = MojBrojLogic.calcScore(oppRes, myRes,  targetNumber, !amIInitiatorThisRound);

        myTotalScore  += myPts;
        oppTotalScore += oppPts;
        refreshScores();

        Toast.makeText(this,
                "Runda " + currentRound + ": Ti=" + (myRes == Integer.MIN_VALUE ? "ništa" : myRes)
                        + " Prot=" + oppRes + " Traženi=" + targetNumber + " → +" + myPts,
                Toast.LENGTH_LONG).show();

        handler.postDelayed(() -> proceedAfterRound(currentRound), 2500);
    }

    // ── Napredovanje ──────────────────────────────────────────────────────────

    private void proceedAfterRound(int round) {
        if (round < 2) {
            startRound(2);
        } else {
            endGame();
        }
    }

    private void endGame() {
        stopRoundTimer();
        setInputEnabled(false);
        faza = 3;

        if (!isGuest) {
            boolean exact = (myResult1 == targetNumber || myResult2 == targetNumber);
            new StatsRepository().saveMojBrojResult(exact, myTotalScore,
                    new StatsRepository.Callback<Void>() {
                        @Override public void onSuccess(Void r) {}
                        @Override public void onError(Exception e) {}
                    });
        }

        if (matchRef != null && myId != null) {
            // Upiši moj ukupan score
            String myKey = isPlayer1 ? "player1" : "player2";
            matchRef.getParent().child("mojBrojFinalScore").child(myKey).setValue(myTotalScore)
                    .addOnSuccessListener(v -> waitForOpponentFinalScore());
        } else {
            // Solo/guest — idi odmah
            handler.postDelayed(this::goToResults, 2000);
            Toast.makeText(this, "Moj broj završen! Ukupno: " + myTotalScore, Toast.LENGTH_SHORT).show();
            btnNext.setEnabled(true);
        }
    }

    private void waitForOpponentFinalScore() {
        String oppKey = isPlayer1 ? "player2" : "player1";
        matchRef.getParent().child("mojBrojFinalScore").child(oppKey)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snap) {
                        Integer oppFinal = snap.getValue(Integer.class);
                        if (oppFinal == null) {
                            // Nije još stiglo, čekaj malo pa proveri ponovo
                            handler.postDelayed(() -> waitForOpponentFinalScore(), 500);
                            return;
                        }
                        // Imamo oba finalna skora — prikaži i idi na Results
                        oppTotalScore = oppFinal;
                        refreshScores();
                        Toast.makeText(MojBrojActivity.this,
                                "Moj broj završen! Ukupno: " + myTotalScore, Toast.LENGTH_SHORT).show();
                        handler.postDelayed(MojBrojActivity.this::goToResults, 2000);
                    }
                    @Override public void onCancelled(DatabaseError e) {}
                });
    }

    private void goToResults() {
        stopRoundTimer();

        boolean isChallengeMode = getIntent().getBooleanExtra("isChallengeMode", false);
        if (isChallengeMode) {
            Intent result = new Intent();
            result.putExtra("gameScore", prevMyScore + myTotalScore);
            setResult(RESULT_OK, result);
            finish();
            return;
        }

        Intent intent = new Intent(this, ResultsActivity.class);
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

    private void forfeit() {
        // otkazi sve lokalne tajmere/listenere specificne za ovu igru (vec postoji kod za to)
        if (roundTimer != null) roundTimer.cancel();
        if (matchRef != null) matchRef.getParent().child("info").child("status").setValue("forfeit_" + myId);
        removeForfeitListener();
        goToResultsAsLoser();
    }

    private void handleOpponentForfeit() {
        stopRoundTimer();
        stopAutoStop();
        removeForfeitListener();
        Intent intent = new Intent(this, com.example.slagalica.ui.main.ResultsActivity.class);
        intent.putExtra("isGuest", isGuest);
        intent.putExtra("isFriendly", isFriendly);
        intent.putExtra("totalMyScore", prevMyScore + myTotalScore);
        intent.putExtra("totalOpponentScore", 0);
        startActivity(intent);
        finish();
    }

    private void goToResultsAsLoser() {
        Intent intent = new Intent(this, com.example.slagalica.ui.main.ResultsActivity.class);
        intent.putExtra("isGuest", isGuest);
        intent.putExtra("isFriendly", isFriendly);
        intent.putExtra("totalMyScore", 0);        // forsiran gubitak, 0 bodova => bez bonusa
        intent.putExtra("totalOpponentScore", 1);  // bilo koji broj > 0, garantuje iWon=false
        startActivity(intent);
        finish();
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void setInputEnabled(boolean enabled) {
        etIzraz.setEnabled(enabled);
        btnPotvrdi.setEnabled(enabled);
        for (MaterialButton b : btnBrojevi) b.setEnabled(enabled);
        findViewById(R.id.btnPlus).setEnabled(enabled);
        findViewById(R.id.btnMinus).setEnabled(enabled);
        findViewById(R.id.btnMul).setEnabled(enabled);
        findViewById(R.id.btnDiv).setEnabled(enabled);
        findViewById(R.id.btnLeftParen).setEnabled(enabled);
        findViewById(R.id.btnRightParen).setEnabled(enabled);
        findViewById(R.id.btnDelete).setEnabled(enabled);
        findViewById(R.id.btnReset).setEnabled(enabled);
    }

    private void addOperator(String op) { if (faza == 2) addToExpression(op); }
    private void addToExpression(String val) { izraz.append(val); refreshExpression(); }
    private void refreshExpression() { etIzraz.setText(izraz.toString()); }
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


    private void resetNumberDisplay() {
        for (int i = 0; i < 6; i++) { tvBrojevi[i].setText("?"); btnBrojevi[i].setText("?"); }
    }
    private void setStatus(String msg) {
        if (tvStatus != null) tvStatus.setText(msg);
    }

    private void stopRoundTimer() { if (roundTimer != null) { roundTimer.cancel(); roundTimer = null; } }
    private void stopAutoStop()   { if (autoStopTimer != null) { autoStopTimer.cancel(); autoStopTimer = null; } }

    @Override
    protected void onDestroy() {
        stopRoundTimer(); stopAutoStop();
        removeForfeitListener();
        handler.removeCallbacksAndMessages(null);
        removeRoundListener();
        super.onDestroy();
    }
}