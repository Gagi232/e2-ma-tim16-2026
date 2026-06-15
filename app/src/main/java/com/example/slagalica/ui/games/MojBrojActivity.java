package com.example.slagalica.ui.games;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.slagalica.R;
import com.example.slagalica.data.repository.StatsRepository;
import com.example.slagalica.logic.MojBrojLogic;
import com.example.slagalica.ui.main.GuestActivity;
import com.example.slagalica.ui.main.ResultsActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MojBrojActivity extends AppCompatActivity {

    // UI
    private TextView tvTrazeniBroj, tvTimer, tvMyScore, tvOppScore, tvRunda;
    private TextView[] tvBrojevi = new TextView[6];
    private MaterialButton[] btnBrojevi = new MaterialButton[6];
    private TextInputEditText etIzraz;
    private ProgressBar pbTime;
    private MaterialButton btnNext;

    // Stanje: 0=ceka prvi STOP, 1=ceka drugi STOP, 2=igra, 3=zavrseno
    private int faza = 0;
    private int currentRound = 1;
    private boolean isMyTurn = true;

    private int targetNumber = 0;
    private final StringBuilder izraz = new StringBuilder();

    // Rezultati rundi (Integer.MIN_VALUE = nista uneto)
    private int myResult1  = Integer.MIN_VALUE;
    private int myResult2  = Integer.MIN_VALUE;
    private int oppResult1 = Integer.MIN_VALUE;
    private int oppResult2 = Integer.MIN_VALUE;

    private int myTotalScore  = 0;  // bodovi samo za ovu igru (prikaz)
    private int oppTotalScore = 0;
    private int prevMyScore   = 0;  // kumulativ iz prethodnih igara
    private int prevOppScore  = 0;

    // Match podaci
    private String matchId, myId, opponentId;
    private boolean isPlayer1, isGuest;

    // Timeri
    private CountDownTimer roundTimer    = null;
    private CountDownTimer autoStopTimer = null;

    // Firebase
    private DatabaseReference matchRef = null;

    private final Random random = new Random();

    // Shake senzor
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastShakeTime = 0;
    private static final float SHAKE_THRESHOLD = 2.7f; // G-sila za detekciju
    private static final int SHAKE_COOLDOWN_MS = 600;  // min. razmak između detekcija

    private final SensorEventListener shakeListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            float gForce = (float) Math.sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH;
            if (gForce > SHAKE_THRESHOLD) {
                long now = System.currentTimeMillis();
                if (now - lastShakeTime < SHAKE_COOLDOWN_MS) return;
                lastShakeTime = now;
                // Shake = isto što i klik na STOP (samo u fazi 0 ili 1)
                if (faza == 0 || faza == 1) {
                    runOnUiThread(() -> handleStop());
                }
            }
        }
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    // -------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moj_broj);

        isGuest       = getIntent().getBooleanExtra("isGuest", false);
        matchId       = getIntent().getStringExtra("matchId");
        myId          = getIntent().getStringExtra("myId");
        opponentId    = getIntent().getStringExtra("opponentId");
        isPlayer1     = getIntent().getBooleanExtra("isPlayer1", true);
        prevMyScore  = getIntent().getIntExtra("totalMyScore", 0);
        prevOppScore = getIntent().getIntExtra("totalOpponentScore", 0);

        if (!isGuest && matchId != null) {
            matchRef = FirebaseDatabase.getInstance()
                    .getReference("activeMatches")
                    .child(matchId)
                    .child("mojBroj");
        }

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        bindViews();
        setupButtons();
        startRound(1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(
                    shakeListener, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(shakeListener);
        }
    }

    // ── Bind ------------------------------------------------------------------

    private void bindViews() {
        tvTrazeniBroj = findViewById(R.id.tvTrazeniBroj);
        tvTimer       = findViewById(R.id.tvTimer);
        tvMyScore     = findViewById(R.id.tvMyScore);
        tvOppScore    = findViewById(R.id.tvOpponentScore);
        tvRunda       = findViewById(R.id.tvRunda);
        etIzraz       = findViewById(R.id.etIzraz);
        pbTime        = findViewById(R.id.pbTime);
        btnNext       = findViewById(R.id.btnNext);

        tvBrojevi[0] = findViewById(R.id.tvBroj1);
        tvBrojevi[1] = findViewById(R.id.tvBroj2);
        tvBrojevi[2] = findViewById(R.id.tvBroj3);
        tvBrojevi[3] = findViewById(R.id.tvBroj4);
        tvBrojevi[4] = findViewById(R.id.tvBroj5);
        tvBrojevi[5] = findViewById(R.id.tvBroj6);

        btnBrojevi[0] = findViewById(R.id.btnB1);
        btnBrojevi[1] = findViewById(R.id.btnB2);
        btnBrojevi[2] = findViewById(R.id.btnB3);
        btnBrojevi[3] = findViewById(R.id.btnB4);
        btnBrojevi[4] = findViewById(R.id.btnB5);
        btnBrojevi[5] = findViewById(R.id.btnB6);
    }

    private void setupButtons() {
        for (int i = 0; i < btnBrojevi.length; i++) {
            final int idx = i;
            btnBrojevi[i].setOnClickListener(v -> {
                if (faza != 2 || !isMyTurn) return;
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
            if (faza != 2 || !isMyTurn || izraz.length() == 0) return;
            izraz.deleteCharAt(izraz.length() - 1);
            refreshExpression();
        });

        findViewById(R.id.btnReset).setOnClickListener(v -> {
            if (faza != 2 || !isMyTurn) return;
            izraz.setLength(0);
            refreshExpression();
        });

        findViewById(R.id.btnStop).setOnClickListener(v -> handleStop());
        findViewById(R.id.btnPotvrdi).setOnClickListener(v -> handlePotvrdi());

        findViewById(R.id.btnPredaj).setOnClickListener(v -> forfeit());

        btnNext.setEnabled(false);
        btnNext.setOnClickListener(v -> goToResults());
    }

    // ── Runde ----------------------------------------------------------------

    private void startRound(int round) {
        currentRound = round;
        // Runda 1 -> player1 ide prvi; Runda 2 -> player2 ide prvi
        // U guest/solo modu oba igraca je isti korisnik — uvek dozvoli unos
        isMyTurn = isGuest || matchId == null || (round == 1) == isPlayer1;

        faza = 0;
        izraz.setLength(0);
        refreshExpression();
        resetNumberDisplay();

        tvTrazeniBroj.setText("?");
        tvTimer.setText("60");
        tvRunda.setText("Runda " + round + "/2");
        refreshScores();

        if (!isMyTurn && !isGuest && matchRef != null) {
            waitForOpponentRound(round);
        }
    }

    // ── STOP ------------------------------------------------------------------

    private void handleStop() {
        if (faza == 0) {
            targetNumber = 100 + random.nextInt(900);
            tvTrazeniBroj.setText(String.valueOf(targetNumber));

            if (matchRef != null) {
                Map<String, Object> data = new HashMap<>();
                data.put("target", targetNumber);
                matchRef.child("round" + currentRound).setValue(data);
            }

            faza = 1;
            beginAutoStop();

        } else if (faza == 1) {
            stopAutoStop();
            revealNumbers();
            faza = 2;
            beginRoundTimer();
        }
    }

    private void revealNumbers() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 4; i++) list.add(1 + random.nextInt(9));
        int[] tens  = {10, 15, 20};
        int[] large = {25, 50, 75, 100};
        list.add(tens[random.nextInt(tens.length)]);
        list.add(large[random.nextInt(large.length)]);
        Collections.shuffle(list, random);

        for (int i = 0; i < 6; i++) {
            String val = String.valueOf(list.get(i));
            tvBrojevi[i].setText(val);
            btnBrojevi[i].setText(val);
        }
    }

    private void resetNumberDisplay() {
        for (int i = 0; i < 6; i++) {
            tvBrojevi[i].setText("?");
            btnBrojevi[i].setText("?");
        }
    }

    // ── Potvrdi --------------------------------------------------------------

    private void handlePotvrdi() {
        if (faza != 2 || !isMyTurn) return;

        String expr = izraz.toString().trim();
        if (expr.isEmpty()) {
            Toast.makeText(this, "Unesi izraz!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Zameni dugmad oznake sa pravim operatorima za evaluaciju
        String evalExpr = expr.replace("x", "*").replace("/", "/");
        int result = MojBrojLogic.evaluate(evalExpr);

        if (result == Integer.MIN_VALUE) {
            Toast.makeText(this, "Neispravan izraz!", Toast.LENGTH_SHORT).show();
            return;
        }

        stopRoundTimer();
        faza = 3;

        if (currentRound == 1) myResult1 = result;
        else                   myResult2 = result;

        boolean exact = (result == targetNumber);
        Toast.makeText(this,
                exact ? "Tacno! " + result : "Rezultat: " + result + " (trazeni: " + targetNumber + ")",
                Toast.LENGTH_SHORT).show();

        if (matchRef != null && myId != null) {
            matchRef.child("round" + currentRound)
                    .child("results").child(myId).setValue(result);
        }

        afterMyTurn();
    }

    // ── Posle mog poteza -----------------------------------------------------

    private void afterMyTurn() {
        if (isGuest || matchRef == null) {
            simulateOpponent();
        } else {
            listenForOpponentResult(currentRound);
        }
    }

    private void simulateOpponent() {
        int oppResult;
        if (random.nextBoolean()) {
            oppResult = targetNumber;
        } else {
            oppResult = targetNumber + random.nextInt(50) - 25;
        }
        if (currentRound == 1) oppResult1 = oppResult;
        else                   oppResult2 = oppResult;
        finishRound();
    }

    // ── Firebase cekanje -----------------------------------------------------

    private void waitForOpponentRound(int round) {
        matchRef.child("round" + round).child("target")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snap) {
                        Integer t = snap.getValue(Integer.class);
                        if (t == null) {
                            new android.os.Handler().postDelayed(
                                    () -> waitForOpponentRound(round), 500);
                            return;
                        }
                        targetNumber = t;
                        tvTrazeniBroj.setText(String.valueOf(targetNumber));
                        listenForOpponentResult(round);
                    }
                    @Override
                    public void onCancelled(DatabaseError e) {}
                });
    }

    private void listenForOpponentResult(int round) {
        if (opponentId == null) return;
        matchRef.child("round" + round).child("results").child(opponentId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snap) {
                        Integer r = snap.getValue(Integer.class);
                        if (r == null) {
                            new android.os.Handler().postDelayed(
                                    () -> listenForOpponentResult(round), 500);
                            return;
                        }
                        if (round == 1) oppResult1 = r;
                        else            oppResult2 = r;
                        runOnUiThread(() -> finishRound());
                    }
                    @Override
                    public void onCancelled(DatabaseError e) {}
                });
    }

    // ── Kraj runde -----------------------------------------------------------

    private void finishRound() {
        stopRoundTimer();
        stopAutoStop();

        int myRes  = (currentRound == 1) ? myResult1 : myResult2;
        int oppRes = (currentRound == 1) ? oppResult1 : oppResult2;

        int pts    = MojBrojLogic.calcScore(myRes, oppRes, targetNumber, isMyTurn);
        int oppPts = MojBrojLogic.calcScore(oppRes, myRes, targetNumber, !isMyTurn);

        myTotalScore  += pts;
        oppTotalScore += oppPts;
        refreshScores();

        Toast.makeText(this,
                "Runda " + currentRound + ": Ti=" + myRes
                        + " Prot=" + oppRes
                        + " Trazeni=" + targetNumber
                        + " -> +" + pts,
                Toast.LENGTH_LONG).show();

        if (currentRound < 2) {
            tvRunda.postDelayed(() -> startRound(2), 2500);
        } else {
            endGame();
        }
    }

    // ── Kraj igre ------------------------------------------------------------

    private void endGame() {
        stopRoundTimer();

        if (!isGuest) {
            boolean exact = (myResult1 == targetNumber || myResult2 == targetNumber);
            // myTotalScore sada počinje od 0 i sadrži samo Moj Broj bodove
            new StatsRepository().saveMojBrojResult(exact, myTotalScore,
                    new StatsRepository.Callback<Void>() {
                        @Override public void onSuccess(Void r) {}
                        @Override public void onError(Exception e) {}
                    });
        }

        if (matchRef != null && myId != null) {
            matchRef.getParent().child("mojBrojScore").child(myId).setValue(myTotalScore);
        }

        btnNext.setEnabled(true);
        Toast.makeText(this, "Moj broj zavrsen! Ukupno: " + myTotalScore, Toast.LENGTH_SHORT).show();
    }

    private void goToResults() {
        stopRoundTimer();
        Intent intent = new Intent(this, ResultsActivity.class);
        intent.putExtra("isGuest",            isGuest);
        intent.putExtra("matchId",            matchId);
        intent.putExtra("myId",               myId);
        intent.putExtra("opponentId",         opponentId);
        intent.putExtra("isPlayer1",          isPlayer1);
        intent.putExtra("totalMyScore",       prevMyScore  + myTotalScore);
        intent.putExtra("totalOpponentScore", prevOppScore + oppTotalScore);
        startActivity(intent);
        finish();
    }

    private void forfeit() {
        stopRoundTimer();
        stopAutoStop();
        if (matchRef != null && myId != null) {
            matchRef.getParent().child("status").setValue("forfeit_" + myId);
        }
        Intent i = isGuest
                ? new Intent(this, GuestActivity.class)
                : new Intent(this, com.example.slagalica.MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
        finish();
    }

    // ── Timeri ---------------------------------------------------------------

    private void beginRoundTimer() {
        stopRoundTimer();
        pbTime.setVisibility(View.VISIBLE);
        pbTime.setMax(60);
        pbTime.setProgress(60);
        roundTimer = new CountDownTimer(60_000, 1_000) {
            @Override
            public void onTick(long ms) {
                int s = (int)(ms / 1000);
                pbTime.setProgress(s);
                tvTimer.setText(String.valueOf(s));
            }
            @Override
            public void onFinish() {
                pbTime.setProgress(0);
                tvTimer.setText("0");
                faza = 3;
                afterMyTurn();
            }
        }.start();
    }

    private void beginAutoStop() {
        stopAutoStop();
        autoStopTimer = new CountDownTimer(5_000, 1_000) {
            @Override
            public void onTick(long ms) {}
            @Override
            public void onFinish() {
                if (faza == 1) {
                    revealNumbers();
                    faza = 2;
                    beginRoundTimer();
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

    private void stopAutoStop() {
        if (autoStopTimer != null) {
            autoStopTimer.cancel();
            autoStopTimer = null;
        }
    }

    // ── UI helpers ------------------------------------------------------------

    private void addOperator(String op) {
        if (faza != 2 || !isMyTurn) return;
        addToExpression(op);
    }

    private void addToExpression(String val) {
        izraz.append(val);
        refreshExpression();
    }

    private void refreshExpression() {
        etIzraz.setText(izraz.toString());
    }

    private void refreshScores() {
        tvMyScore.setText(String.valueOf(myTotalScore));
        tvOppScore.setText(String.valueOf(oppTotalScore));
    }

    @Override
    protected void onDestroy() {
        stopRoundTimer();
        stopAutoStop();
        super.onDestroy();
    }
}