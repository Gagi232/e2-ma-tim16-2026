package com.example.slagalica.ui.games;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.slagalica.MainActivity;
import com.example.slagalica.R;
import com.example.slagalica.data.model.SpojnicePar;
import com.example.slagalica.data.model.SpojniceSet;
import com.example.slagalica.data.repository.GameRepository;
import com.example.slagalica.data.repository.StatsRepository;
import com.example.slagalica.ui.main.GuestActivity;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.*;

import java.util.*;

public class SpojniceActivity extends AppCompatActivity {

    private static final int  PAIRS        = 5;
    private static final long TURN_TIME_MS = 30_000;
    private static final int  PTS_PER_PAIR = 2;

    // ── Match podaci ──────────────────────────────────────────────────────────
    private String  matchId, myId, opponentId;
    private boolean isPlayer1, isGuest;

    // Bodovi samo za ovu igru (prikaz u UI)
    private int totalMy  = 0;
    private int totalOpp = 0;
    // Kumulativ iz prethodnih igara (ne prikazuje se, samo se prosleđuje dalje)
    private int prevMy   = 0;
    private int prevOpp  = 0;

    // ── Stanje igre ───────────────────────────────────────────────────────────
    private SpojniceSet set;
    // rightPairIdx[i] = indeks para čiji desni element stoji na poziciji i u UI-ju
    private final int[]      rightPairIdx = new int[PAIRS];
    private int              currentRound   = 1;
    private boolean          isMyTurn       = false;
    private boolean          isSecondChance = false;
    private int              selectedLeft   = -1; // -1 = ništa nije izabrano
    // leftSelected[i] = true ako sam kliknuo na levi element i (čekam desni)
    private final boolean[]  leftSelected   = new boolean[PAIRS];

    // Konekcije: leftIdx → rightPairIdx (vrednost = tačan par ako jednaki)
    private final Map<Integer, Integer> myConns  = new HashMap<>();
    private final Map<Integer, Integer> oppConns = new HashMap<>();

    private int myRoundScore  = 0;
    private int oppRoundScore = 0;

    private CountDownTimer   timer;
    private ValueEventListener phaseListener;

    // ── UI ────────────────────────────────────────────────────────────────────
    // activity_spojnice.xml ima STATIČKE TextViews: tvLeft1-5 i tvRight1-5
    // NE postoje: tvTurn, tvCategory — koristimo tvRound i tvGameTitle umesto njih
    private TextView   tvRound, tvTimer, tvMyScore, tvOppScore, tvGameTitle;
    private TextView[] leftViews  = new TextView[PAIRS];
    private TextView[] rightViews = new TextView[PAIRS];

    // ── Firebase ──────────────────────────────────────────────────────────────
    private DatabaseReference matchRef;

    private final GameRepository gameRepo = new GameRepository();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spojnice);

        isGuest    = getIntent().getBooleanExtra("isGuest", false);
        matchId    = getIntent().getStringExtra("matchId");
        myId       = getIntent().getStringExtra("myId");
        opponentId = getIntent().getStringExtra("opponentId");
        isPlayer1  = getIntent().getBooleanExtra("isPlayer1", true);
        prevMy  = getIntent().getIntExtra("kzzMyScore", 0);
        prevOpp = getIntent().getIntExtra("kzzOpponentScore", 0);

        initViews();
        updateScoreUI();

        if (!isGuest && matchId != null)
            matchRef = FirebaseDatabase.getInstance()
                    .getReference("activeMatches").child(matchId).child("spojnice");

        // Dugme "Sledeća igra" sakrivamo — igra se sama završava
        findViewById(R.id.btnFinish).setVisibility(android.view.View.GONE);
        findViewById(R.id.btnLeave).setOnClickListener(v -> forfeit());

        startRound(1);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
    }

    // ── Init pogleda ─────────────────────────────────────────────────────────

    private void initViews() {
        // tvGameTitle koristimo za kategoriju (nema tvCategory u XML-u)
        tvGameTitle = findViewById(R.id.tvGameTitle);
        tvRound     = findViewById(R.id.tvRound);      // koristimo i za info o redu
        tvTimer     = findViewById(R.id.tvTimer);
        tvMyScore   = findViewById(R.id.tvMyScore);
        tvOppScore  = findViewById(R.id.tvOpponentScore);

        // Statički TextViews iz XML-a — tvLeft1..5 i tvRight1..5
        leftViews[0]  = findViewById(R.id.tvLeft1);
        leftViews[1]  = findViewById(R.id.tvLeft2);
        leftViews[2]  = findViewById(R.id.tvLeft3);
        leftViews[3]  = findViewById(R.id.tvLeft4);
        leftViews[4]  = findViewById(R.id.tvLeft5);

        rightViews[0] = findViewById(R.id.tvRight1);
        rightViews[1] = findViewById(R.id.tvRight2);
        rightViews[2] = findViewById(R.id.tvRight3);
        rightViews[3] = findViewById(R.id.tvRight4);
        rightViews[4] = findViewById(R.id.tvRight5);
    }

    // ── Runda ─────────────────────────────────────────────────────────────────

    private void startRound(int round) {
        currentRound   = round;
        isSecondChance = false;
        myConns.clear();
        oppConns.clear();
        Arrays.fill(leftSelected, false);
        selectedLeft   = -1;
        myRoundScore   = 0;
        oppRoundScore  = 0;

        tvRound.setText("Runda " + round + "/2");
        resetAllColors();

        // Runda 1 → Player1 ide prvi; Runda 2 → Player2 ide prvi
        boolean iGoFirst = (round == 1) ? isPlayer1 : !isPlayer1;

        if (isGuest || matchRef == null) {
            // Solo / gost mod
            gameRepo.getRandomSpojniceSet(
                    s -> { set = s; buildUI(); beginMyTurn(); },
                    e -> showError());
            return;
        }

        String roundKey = "round" + round;
        if (iGoFirst) {
            // Ja biram set i upisujem setId u DB
            gameRepo.getRandomSpojniceSet(s -> {
                set = s;
                matchRef.child(roundKey).child("setId").setValue(s.getId());
                matchRef.child(roundKey).child("phase").setValue("first");
                buildUI();
                beginMyTurn();
            }, e -> showError());
        } else {
            // Čekam setId od protivnika
            pollForSetId(roundKey);
        }
    }

    private void pollForSetId(String roundKey) {
        matchRef.child(roundKey).child("setId").addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override public void onDataChange(DataSnapshot snap) {
                        String setId = snap.getValue(String.class);
                        if (setId == null) {
                            new Handler().postDelayed(() -> pollForSetId(roundKey), 700);
                            return;
                        }
                        gameRepo.getSpojniceSetById(setId, s -> {
                            set = s;
                            buildUI();
                            waitForOpponentTurn(roundKey);
                        }, e -> showError());
                    }
                    @Override public void onCancelled(DatabaseError e) { showError(); }
                });
    }

    // ── Izgradnja UI-a ────────────────────────────────────────────────────────
    // Leva kolona: redom; desna kolona: izmešana.
    // Koristimo statičke tvLeft1-5 i tvRight1-5 iz XML-a.

    private void buildUI() {
        // Kategorija prikazujemo u "tvGameTitle" (jedini odgovarajući TextView)
        tvGameTitle.setText(set.getCategory());

        // Leva strana — u redosledu
        List<SpojnicePar> pairs = set.getPairs();
        for (int i = 0; i < PAIRS; i++) {
            leftViews[i].setText(pairs.get(i).getLeft());
            leftViews[i].setEnabled(false); // uključujemo ih tek na beginMyTurn()
        }

        // Desna strana — izmešana; pamtimo mapiranje pozicija → par
        List<Integer> order = new ArrayList<>(Arrays.asList(0, 1, 2, 3, 4));
        Collections.shuffle(order);
        for (int i = 0; i < PAIRS; i++) {
            rightPairIdx[i] = order.get(i);
            rightViews[i].setText(pairs.get(order.get(i)).getRight());
            rightViews[i].setEnabled(false);
        }

        attachClickListeners();
    }

    private void attachClickListeners() {
        for (int i = 0; i < PAIRS; i++) {
            final int li = i;
            leftViews[i].setOnClickListener(v -> onLeftClicked(li));
        }
        for (int i = 0; i < PAIRS; i++) {
            final int ri = i; // pozicija u UI-ju (NE indeks para!)
            rightViews[i].setOnClickListener(v -> onRightClicked(ri));
        }
    }

    // ── Upravljanje potezima ──────────────────────────────────────────────────

    private void beginMyTurn() {
        isMyTurn = true;
        tvRound.setText("Runda " + currentRound + "/2 — " +
                (isSecondChance ? "Tvoj red (preostali)!" : "Tvoj red!"));
        enableAvailableViews(true);
        startTimer(() -> onMyTurnTimeUp());
    }

    private void waitForOpponentTurn(String roundKey) {
        isMyTurn = false;
        tvRound.setText("Runda " + currentRound + "/2 — Čeka protivnik...");
        enableAvailableViews(false);

        removePhaseListener(roundKey);
        phaseListener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snap) {
                String phase = snap.getValue(String.class);
                if ("second".equals(phase)) {
                    removePhaseListener(roundKey);
                    loadOppConnections(roundKey, () -> {
                        isSecondChance = true;
                        beginMyTurn();
                    });
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        matchRef.child(roundKey).child("phase").addValueEventListener(phaseListener);
    }

    private void loadOppConnections(String roundKey, Runnable then) {
        if (opponentId == null) { then.run(); return; }
        matchRef.child(roundKey).child("connections").child(opponentId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(DataSnapshot snap) {
                        for (DataSnapshot e : snap.getChildren())
                            oppConns.put(Integer.parseInt(e.getKey()),
                                    e.getValue(Integer.class));
                        markOppOccupied();
                        then.run();
                    }
                    @Override public void onCancelled(DatabaseError e) { then.run(); }
                });
    }

    /** Vizuelno označi parove koje je protivnik već zauzeo. */
    private void markOppOccupied() {
        for (Map.Entry<Integer, Integer> e : oppConns.entrySet()) {
            int li = e.getKey(); // indeks lijevog pojma
            int pi = e.getValue(); // indeks para koji je spojen
            // Boja: tačno (li==pi) zelena, netačno narandžasta
            int color = (li == pi) ? 0xFF4CAF50 : 0xFFFF9800;
            leftViews[li].setEnabled(false);
            leftViews[li].setBackgroundColor(color);
            leftViews[li].setAlpha(0.6f);
            // Nađi desni view koji odgovara pi
            for (int i = 0; i < PAIRS; i++) {
                if (rightPairIdx[i] == pi) {
                    rightViews[i].setEnabled(false);
                    rightViews[i].setBackgroundColor(color);
                    rightViews[i].setAlpha(0.6f);
                    break;
                }
            }
        }
    }

    private void onMyTurnTimeUp() {
        isMyTurn = false;
        enableAvailableViews(false);

        if (isSecondChance || isGuest || matchRef == null) {
            endRound();
        } else {
            String roundKey = "round" + currentRound;
            writeMyConnections(roundKey, () -> {
                matchRef.child(roundKey).child("phase").setValue("second");
                waitForDone(roundKey);
            });
        }
    }

    private void waitForDone(String roundKey) {
        tvRound.setText("Runda " + currentRound + "/2 — Čeka protivnik...");
        removePhaseListener(roundKey);
        phaseListener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snap) {
                if ("done".equals(snap.getValue(String.class))) {
                    removePhaseListener(roundKey);
                    endRound();
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        matchRef.child(roundKey).child("phase").addValueEventListener(phaseListener);
    }

    // ── Klikovi na pojmove ────────────────────────────────────────────────────

    private void onLeftClicked(int leftIdx) {
        if (!isMyTurn || myConns.containsKey(leftIdx) || oppConns.containsKey(leftIdx)) return;

        // Poništi prethodni izbor
        if (selectedLeft >= 0) {
            leftViews[selectedLeft].setBackgroundColor(
                    getResources().getColor(R.color.white, getTheme()));
        }
        selectedLeft = leftIdx;
        leftViews[leftIdx].setBackgroundColor(
                getResources().getColor(R.color.primary_yellow, getTheme()));
    }

    private void onRightClicked(int rightUiPos) {
        // rightUiPos = pozicija u UI-ju; rightPairIdx[rightUiPos] = indeks para
        if (!isMyTurn || selectedLeft < 0) return;

        int pairIdxOfRight = rightPairIdx[rightUiPos];

        // Zauzeto?
        if (myConns.containsValue(pairIdxOfRight) || oppConns.containsValue(pairIdxOfRight))
            return;

        boolean correct = (selectedLeft == pairIdxOfRight);
        myConns.put(selectedLeft, pairIdxOfRight);

        int color = correct ? 0xFF4CAF50 : 0xFFFF9800;
        leftViews[selectedLeft].setBackgroundColor(color);
        leftViews[selectedLeft].setEnabled(false);
        rightViews[rightUiPos].setBackgroundColor(color);
        rightViews[rightUiPos].setEnabled(false);
        selectedLeft = -1;

        // Ako su svi parovi popunjeni, završavamo red pre isteka tajmera
        if ((myConns.size() + oppConns.size()) >= PAIRS) {
            if (timer != null) timer.cancel();
            onMyTurnTimeUp();
        }
    }

    // ── Kraj runde ────────────────────────────────────────────────────────────

    private void endRound() {
        if (timer != null) timer.cancel();
        String roundKey = "round" + currentRound;

        if (matchRef != null) {
            writeMyConnections(roundKey, () -> {
                matchRef.child(roundKey).child("phase").setValue("done");
                // Učitavamo sve konekcije radi tačnog bodovanja
                matchRef.child(roundKey).child("connections")
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override public void onDataChange(DataSnapshot snap) {
                                myConns.clear(); oppConns.clear();
                                if (myId != null)
                                    for (DataSnapshot e : snap.child(myId).getChildren())
                                        myConns.put(Integer.parseInt(e.getKey()),
                                                e.getValue(Integer.class));
                                if (opponentId != null)
                                    for (DataSnapshot e : snap.child(opponentId).getChildren())
                                        oppConns.put(Integer.parseInt(e.getKey()),
                                                e.getValue(Integer.class));
                                calcAndProceed();
                            }
                            @Override public void onCancelled(DatabaseError e) { calcAndProceed(); }
                        });
            });
        } else {
            calcAndProceed();
        }
    }

    private void calcAndProceed() {
        // Spec: 2 boda za svaki tačno spojen par (leftIdx == rightPairIdx)
        for (Map.Entry<Integer, Integer> e : myConns.entrySet())
            if (e.getKey().equals(e.getValue())) myRoundScore += PTS_PER_PAIR;
        for (Map.Entry<Integer, Integer> e : oppConns.entrySet())
            if (e.getKey().equals(e.getValue())) oppRoundScore += PTS_PER_PAIR;

        totalMy  += myRoundScore;
        totalOpp += oppRoundScore;
        updateScoreUI();

        if (currentRound == 1) {
            tvRound.setText("Runda 1 završena! +" + myRoundScore + " bodova. Pripremi se...");
            new Handler().postDelayed(() -> startRound(2), 2500);
        } else {
            finishGame();
        }
    }

    // ── Pomagači ─────────────────────────────────────────────────────────────

    private void writeMyConnections(String roundKey, Runnable then) {
        if (matchRef == null || myId == null) { then.run(); return; }
        Map<String, Object> data = new HashMap<>();
        for (Map.Entry<Integer, Integer> e : myConns.entrySet())
            data.put(String.valueOf(e.getKey()), e.getValue());
        matchRef.child(roundKey).child("connections").child(myId)
                .setValue(data).addOnCompleteListener(t -> then.run());
    }

    private void removePhaseListener(String roundKey) {
        if (phaseListener != null && matchRef != null)
            matchRef.child(roundKey).child("phase").removeEventListener(phaseListener);
        phaseListener = null;
    }

    private void enableAvailableViews(boolean enabled) {
        for (int i = 0; i < PAIRS; i++) {
            if (!myConns.containsKey(i) && !oppConns.containsKey(i))
                leftViews[i].setEnabled(enabled);
        }
        for (int i = 0; i < PAIRS; i++) {
            int pi = rightPairIdx[i];
            if (!myConns.containsValue(pi) && !oppConns.containsValue(pi))
                rightViews[i].setEnabled(enabled);
        }
    }

    private void startTimer(Runnable onDone) {
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(TURN_TIME_MS, 1000) {
            @Override public void onTick(long left) {
                tvTimer.setText(String.valueOf(left / 1000));
            }
            @Override public void onFinish() {
                tvTimer.setText("0");
                onDone.run();
            }
        }.start();
    }

    private void resetAllColors() {
        int white = getResources().getColor(R.color.white, getTheme());
        int yellow = getResources().getColor(R.color.primary_yellow_light, getTheme());
        for (TextView tv : leftViews) {
            if (tv != null) { tv.setBackgroundColor(white); tv.setAlpha(1f); }
        }
        for (TextView tv : rightViews) {
            if (tv != null) { tv.setBackgroundColor(yellow); tv.setAlpha(1f); }
        }
    }

    private void updateScoreUI() {
        if (tvMyScore  != null) tvMyScore.setText(String.valueOf(totalMy));
        if (tvOppScore != null) tvOppScore.setText(String.valueOf(totalOpp));
    }

    private void finishGame() {
        if (timer != null) timer.cancel();
        if (matchRef != null) {
            matchRef.child("finalScore").child(myId).setValue(prevMy + totalMy);
            matchRef.child("done").child(myId).setValue(true);
        }

        // totalMy je sada samo bodovi od Spojnica (počelo od 0)
        int connected = totalMy / 2;
        StatsRepository statsRepo = new StatsRepository();
        statsRepo.saveSpojniceResult(connected, 10, totalMy,
                new StatsRepository.Callback<Void>() {
                    @Override public void onSuccess(Void r) {}
                    @Override public void onError(Exception e) {}
                });

        Intent intent = new Intent(this, AsocijacijeActivity.class);
        intent.putExtra("isGuest",            isGuest);
        intent.putExtra("matchId",            matchId);
        intent.putExtra("myId",               myId);
        intent.putExtra("opponentId",         opponentId);
        intent.putExtra("isPlayer1",          isPlayer1);
        intent.putExtra("totalMyScore",       prevMy + totalMy);
        intent.putExtra("totalOpponentScore", prevOpp + totalOpp);
        startActivity(intent);
        finish();
    }
    private void forfeit() {
        if (timer != null) timer.cancel();
        if (matchRef != null)
            matchRef.getParent().child("status").setValue("forfeit_" + myId);
        Intent i = new Intent(this, isGuest ? GuestActivity.class : MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
        finish();
    }

    private void showError() {
        Toast.makeText(this, "Greška pri učitavanju igre!", Toast.LENGTH_SHORT).show();
        finish();
    }
}