package com.example.slagalica.ui.games;

import android.content.Intent;
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
import com.google.firebase.database.*;

import java.util.*;

public class SpojniceActivity extends AppCompatActivity {

    private static final int  PAIRS        = 5;
    private static final long TURN_TIME_MS = 30_000;
    private static final int  PTS_PER_PAIR = 2;

    // ── Match podaci ──────────────────────────────────────────────────────────
    private String  matchId, myId, opponentId;
    private boolean isPlayer1, isGuest;

    // Bodovi samo za ovu igru (Spojnice)
    private int totalMy  = 0;
    private int totalOpp = 0;
    // Kumulativ iz prethodnih igara — PRIKAZUJEMO ih, ne samo prosleđujemo
    private int prevMy   = 0;
    private int prevOpp  = 0;

    // ── Stanje igre ───────────────────────────────────────────────────────────
    private SpojniceSet set;
    private final int[]     rightPairIdx = new int[PAIRS];
    private int             currentRound   = 1;
    private boolean         isMyTurn       = false;
    private boolean         isSecondChance = false;
    private int             selectedLeft   = -1;

    private final Map<Integer, Integer> myConns  = new HashMap<>();
    private final Map<Integer, Integer> oppConns = new HashMap<>();

    private int myRoundScore  = 0;
    private int oppRoundScore = 0;

    private CountDownTimer    timer;
    private ValueEventListener phaseListener;

    // ── UI ────────────────────────────────────────────────────────────────────
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
        prevMy     = getIntent().getIntExtra("kzzMyScore", 0);
        prevOpp    = getIntent().getIntExtra("kzzOpponentScore", 0);

        initViews();
        updateScoreUI();

        if (!isGuest && matchId != null)
            matchRef = FirebaseDatabase.getInstance()
                    .getReference("activeMatches").child(matchId).child("spojnice");

        findViewById(R.id.btnFinish).setVisibility(android.view.View.GONE);
        findViewById(R.id.btnLeave).setOnClickListener(v -> forfeit());

        startRound(1);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
        removePhaseListener("round" + currentRound);
    }

    // ── Init pogleda ─────────────────────────────────────────────────────────

    private void initViews() {
        tvGameTitle = findViewById(R.id.tvGameTitle);
        tvRound     = findViewById(R.id.tvRound);
        tvTimer     = findViewById(R.id.tvTimer);
        tvMyScore   = findViewById(R.id.tvMyScore);
        tvOppScore  = findViewById(R.id.tvOpponentScore);

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
        selectedLeft   = -1;
        myRoundScore   = 0;
        oppRoundScore  = 0;

        tvRound.setText("Runda " + round + "/2");
        resetAllColors();

        // Runda 1 → Player1 ide prvi; Runda 2 → Player2 ide prvi
        boolean iGoFirst = (round == 1) ? isPlayer1 : !isPlayer1;

        if (isGuest || matchRef == null) {
            gameRepo.getRandomSpojniceSet(
                    s -> { set = s; buildUI(); beginMyTurn(); },
                    e -> showError("Greška pri učitavanju seta"));
            return;
        }

        String roundKey = "round" + round;
        if (iGoFirst) {
            gameRepo.getRandomSpojniceSet(s -> {
                set = s;
                matchRef.child(roundKey).child("setId").setValue(s.getId())
                        .addOnFailureListener(e -> showWriteError("setId", e));
                matchRef.child(roundKey).child("phase").setValue("first")
                        .addOnFailureListener(e -> showWriteError("phase=first", e));
                buildUI();
                beginMyTurn();
            }, e -> showError("Greška pri dobavljanju seta"));
        } else {
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
                        }, e -> showError("Greška pri učitavanju seta po ID-u"));
                    }
                    @Override public void onCancelled(DatabaseError e) {
                        showError("Nemam pristup partiji (setId): " + e.getMessage());
                    }
                });
    }

    // ── Izgradnja UI-a ────────────────────────────────────────────────────────

    private void buildUI() {
        tvGameTitle.setText(set.getCategory());

        List<SpojnicePar> pairs = set.getPairs();
        for (int i = 0; i < PAIRS; i++) {
            leftViews[i].setText(pairs.get(i).getLeft());
            leftViews[i].setEnabled(false);
        }

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
            final int ri = i;
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
            @Override public void onCancelled(DatabaseError e) {
                // Ovo je tačno mesto gde je ranije sve "tiho stajalo" —
                // sad bar vidiš zašto ako pravila nisu ispravna.
                showError("Nemam pristup fazi partije: " + e.getMessage());
            }
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

    private void markOppOccupied() {
        for (Map.Entry<Integer, Integer> e : oppConns.entrySet()) {
            int li = e.getKey();
            int pi = e.getValue();
            int color = (li == pi) ? 0xFF4CAF50 : 0xFFFF9800;
            leftViews[li].setEnabled(false);
            leftViews[li].setBackgroundColor(color);
            leftViews[li].setAlpha(0.6f);
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
                matchRef.child(roundKey).child("phase").setValue("second")
                        .addOnFailureListener(e -> showWriteError("phase=second", e));
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
            @Override public void onCancelled(DatabaseError e) {
                showError("Nemam pristup fazi partije: " + e.getMessage());
            }
        };
        matchRef.child(roundKey).child("phase").addValueEventListener(phaseListener);
    }

    // ── Klikovi na pojmove ────────────────────────────────────────────────────

    private void onLeftClicked(int leftIdx) {
        if (!isMyTurn || myConns.containsKey(leftIdx) || oppConns.containsKey(leftIdx)) return;

        if (selectedLeft >= 0)
            leftViews[selectedLeft].setBackgroundColor(
                    getResources().getColor(R.color.white, getTheme()));

        selectedLeft = leftIdx;
        leftViews[leftIdx].setBackgroundColor(
                getResources().getColor(R.color.primary_yellow, getTheme()));
    }

    private void onRightClicked(int rightUiPos) {
        if (!isMyTurn || selectedLeft < 0) return;

        int pairIdxOfRight = rightPairIdx[rightUiPos];

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
                matchRef.child(roundKey).child("phase").setValue("done")
                        .addOnFailureListener(e -> showWriteError("phase=done", e));
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
                            @Override public void onCancelled(DatabaseError e) {
                                calcAndProceed();
                            }
                        });
            });
        } else {
            calcAndProceed();
        }
    }

    private void calcAndProceed() {
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
                .setValue(data)
                .addOnFailureListener(e -> showWriteError("connections", e))
                .addOnCompleteListener(t -> then.run());
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
        int white  = getResources().getColor(R.color.white, getTheme());
        int yellow = getResources().getColor(R.color.primary_yellow_light, getTheme());
        for (TextView tv : leftViews)  if (tv != null) { tv.setBackgroundColor(white);  tv.setAlpha(1f); }
        for (TextView tv : rightViews) if (tv != null) { tv.setBackgroundColor(yellow); tv.setAlpha(1f); }
    }

    /** Prikazujemo KUMULATIV (prethodne igre + ova igra), ne samo Spojnice bodove. */
    private void updateScoreUI() {
        if (tvMyScore  != null) tvMyScore.setText(String.valueOf(prevMy  + totalMy));
        if (tvOppScore != null) tvOppScore.setText(String.valueOf(prevOpp + totalOpp));
    }

    private void finishGame() {
        if (timer != null) timer.cancel();
        if (matchRef != null) {
            matchRef.child("finalScore").child(myId).setValue(prevMy + totalMy)
                    .addOnFailureListener(e -> showWriteError("finalScore", e));
            matchRef.child("done").child(myId).setValue(true)
                    .addOnFailureListener(e -> showWriteError("done", e));
        }

        int connected = totalMy / 2;
        new StatsRepository().saveSpojniceResult(connected, 10, totalMy,
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
            matchRef.getParent().child("info").child("status").setValue("forfeit_" + myId)
                    .addOnFailureListener(e -> showWriteError("forfeit", e));
        Intent i = new Intent(this, isGuest ? GuestActivity.class : MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
        finish();
    }

    private void showError(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private void showWriteError(String what, Exception e) {
        Toast.makeText(this, "Write error (" + what + "): " + e.getMessage(),
                Toast.LENGTH_LONG).show();
    }
}