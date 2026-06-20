package com.example.slagalica.ui.games;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.data.model.SkockoCombo;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.GameRepository;
import com.example.slagalica.data.repository.StatsRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.logic.LeagueLogic;
import com.example.slagalica.logic.SkockoLogic;
import com.example.slagalica.ui.main.GuestActivity;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SkockoActivity extends AppCompatActivity {

    private static final List<String> SYMBOLS = Arrays.asList("⬛", "⬜", "🔴", "💛", "🔺", "⭐");

    // ── UI ───────────────────────────────────────────────────────────────────
    private TextView tvTimer, tvMyScore, tvOpponentScore, tvRound;
    private TextView[][] tvAttemptFields;
    private LinearLayout[] llAttemptFeedback;
    private TextView[] tvOppFields;
    private LinearLayout llOppFeedback;
    private TextView[] tvSymbolButtons;
    private MaterialButton btnFinish, btnLeave, btnDelete;

    // ── Match State ──────────────────────────────────────────────────────────
    private String matchId, myId, opponentId;
    private boolean isPlayer1, isGuest;
    private DatabaseReference matchRef;

    private SkockoCombo combo;
    private int currentRound = 1;
    private int myScoreTotal = 0;
    private int oppScoreTotal = 0;

    // Bodovi u TRENUTNOJ rundi (čitaju se iz Firebase "scores" čvora svake runde
    // i sabiraju u myScoreTotal/oppScoreTotal tek kad runda završi).
    private int myRoundScore = 0;
    private int oppRoundScore = 0;

    private List<String> currentGuess;
    private int attemptIndex = 0;
    private boolean isMyTurn = false;
    private boolean isOpponentChance = false;

    // FIX: pratimo za koju rundu je UI (grid, dugmad...) već inicijalizovan —
    // ne pogađamo to iz identiteta `combo` objekta. Igrač koji POKREĆE rundu već ima
    // `combo` lokalno postavljen PRE nego što mu stigne echo iz baze, pa stara provera
    // (combo == null || !combo.getId().equals(setId)) nikad nije bila tačna za njega
    // i setupRoundUI() se nikad nije pozivao — grid je ostajao "zaglavljen" iz prošle runde.
    private int uiSetupRound = -1;

    private CountDownTimer timer;
    private final GameRepository repo = new GameRepository();
    private final StatsRepository statsRepo = new StatsRepository();
    private final UserRepository userRepo = new UserRepository();
    private ValueEventListener roundListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skocko);

        isGuest    = getIntent().getBooleanExtra("isGuest", false);
        matchId    = getIntent().getStringExtra("matchId");
        myId       = getIntent().getStringExtra("myId");
        opponentId = getIntent().getStringExtra("opponentId");
        isPlayer1  = getIntent().getBooleanExtra("isPlayer1", true);

        myScoreTotal  = getIntent().getIntExtra("totalMyScore", 0);
        oppScoreTotal = getIntent().getIntExtra("totalOpponentScore", 0);

        bindViews();
        updateTopBar();
        updateScoreUI();

        if (isGuest || matchId == null) {
            loadSolo();
        } else {
            matchRef = FirebaseDatabase.getInstance().getReference("activeMatches").child(matchId).child("skocko");
            startMultiplayerRound(1);
        }
    }

    private void updateScoreUI() {
        tvMyScore.setText(String.valueOf(myScoreTotal));
        tvOpponentScore.setText(String.valueOf(oppScoreTotal));
    }

    private void bindViews() {
        tvTimer         = findViewById(R.id.tvTimer);
        tvMyScore       = findViewById(R.id.tvMyScore);
        tvOpponentScore = findViewById(R.id.tvOpponentScore);
        tvRound         = findViewById(R.id.tvRound);
        btnFinish       = findViewById(R.id.btnFinish);
        btnLeave        = findViewById(R.id.btnLeave);
        btnDelete       = findViewById(R.id.btnDelete);

        // FIX: dugme je u XML-u labelovano "Sledeća igra →" ali zapravo potvrđuje pokušaj
        btnFinish.setText("Potvrdi");

        int[][] attemptFieldIds = {
                { R.id.tvAttempt1Field1, R.id.tvAttempt1Field2, R.id.tvAttempt1Field3, R.id.tvAttempt1Field4 },
                { R.id.tvAttempt2Field1, R.id.tvAttempt2Field2, R.id.tvAttempt2Field3, R.id.tvAttempt2Field4 },
                { R.id.tvAttempt3Field1, R.id.tvAttempt3Field2, R.id.tvAttempt3Field3, R.id.tvAttempt3Field4 },
                { R.id.tvAttempt4Field1, R.id.tvAttempt4Field2, R.id.tvAttempt4Field3, R.id.tvAttempt4Field4 },
                { R.id.tvAttempt5Field1, R.id.tvAttempt5Field2, R.id.tvAttempt5Field3, R.id.tvAttempt5Field4 },
                { R.id.tvAttempt6Field1, R.id.tvAttempt6Field2, R.id.tvAttempt6Field3, R.id.tvAttempt6Field4 },
        };
        int[] feedbackIds = {
                R.id.llFeedback1, R.id.llFeedback2, R.id.llFeedback3,
                R.id.llFeedback4, R.id.llFeedback5, R.id.llFeedback6
        };

        tvAttemptFields   = new TextView[6][4];
        llAttemptFeedback = new LinearLayout[6];
        for (int i = 0; i < 6; i++) {
            llAttemptFeedback[i] = findViewById(feedbackIds[i]);
            for (int f = 0; f < 4; f++)
                tvAttemptFields[i][f] = findViewById(attemptFieldIds[i][f]);
        }

        tvOppFields = new TextView[]{
                findViewById(R.id.tvOppField1), findViewById(R.id.tvOppField2),
                findViewById(R.id.tvOppField3), findViewById(R.id.tvOppField4)
        };
        llOppFeedback = findViewById(R.id.llOppFeedback);

        LinearLayout llRoundSymbols = findViewById(R.id.llRoundSymbols);
        tvSymbolButtons = new TextView[6];
        int symIdx = 0;
        for (int i = 0; i < llRoundSymbols.getChildCount() && symIdx < 6; i++) {
            View child = llRoundSymbols.getChildAt(i);
            if (child instanceof TextView && child.getId() != R.id.tvRound) {
                tvSymbolButtons[symIdx] = (TextView) child;
                final String sym = SYMBOLS.get(symIdx);
                tvSymbolButtons[symIdx].setOnClickListener(v -> onSymbolSelected(sym));
                symIdx++;
            }
        }

        btnDelete.setOnClickListener(v -> deleteLastSymbol());
        btnFinish.setOnClickListener(v -> submitGuess());
        btnLeave.setOnClickListener(v -> forfeit());
    }

    private void updateTopBar() {
        userRepo.getCurrentUser(new UserRepository.Callback<User>() {
            @Override
            public void onSuccess(User user) {
                ((TextView) findViewById(R.id.tvTokens)).setText(String.valueOf(user.getTokens()));
                ((TextView) findViewById(R.id.tvStars)).setText(String.valueOf(user.getStars()));
                int league = LeagueLogic.calculateLeague(user.getStars());
                ((TextView) findViewById(R.id.tvLeague)).setText(LeagueLogic.getLeagueIcon(league));
            }
            @Override public void onError(Exception e) {}
        });
    }

    private void loadSolo() {
        repo.getRandomSkockoCombo(c -> {
            combo = c;
            isMyTurn = true;
            setupRoundUI(1);
            startTimer(60);
        }, e -> showError());
    }

    private void startMultiplayerRound(int round) {
        currentRound = round;
        String roundKey = "round" + round;
        boolean iSetup = (round == 1) ? isPlayer1 : !isPlayer1;

        if (iSetup) {
            repo.getRandomSkockoCombo(c -> {
                combo = c;
                Map<String, Object> data = new HashMap<>();
                data.put("setId", c.getId());
                data.put("phase", "playing");
                // round 1: player1 plays, round 2: player2 plays
                data.put("player", (round==1)?(isPlayer1?myId:opponentId):(isPlayer1?opponentId:myId));
                // FIX: briše stari sadržaj ovog round-a (attempts/results/scores iz prethodnih
                // test-mečeva) pre upisa novog, isto kao kod asocijacija.
                matchRef.child(roundKey).removeValue()
                        .addOnCompleteListener(t ->
                                matchRef.child(roundKey).setValue(data)
                                        .addOnFailureListener(e -> showWriteError("roundInit", e)));
                listenToRound(roundKey);
            }, e -> showError());
        } else {
            listenToRound(roundKey);
        }
    }

    private void listenToRound(String roundKey) {
        if (roundListener != null) matchRef.child(roundKey).removeEventListener(roundListener);
        roundListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                String setId = snapshot.child("setId").getValue(String.class);
                if (setId == null) return;

                // FIX: UI se resetuje tačno jednom po rundi (na osnovu broja runde),
                // bez obzira da li `combo` već lokalno odgovara setId-u.
                if (uiSetupRound != currentRound) {
                    uiSetupRound = currentRound;
                    setupRoundUI(currentRound);
                }

                if (combo == null || !combo.getId().equals(setId)) {
                    // FIX: mora da povuče TAČNO istu kombinaciju po ID-u, ne novu nasumičnu —
                    // u suprotnom igrači imaju različita rešenja.
                    // NAPOMENA: proveri da li getSkockoComboById postoji u GameRepository,
                    // ako ne — dodaj ga (ista šema kao getAssociationById, samo za skocko_combos).
                    repo.getSkockoComboById(setId, c -> {
                        combo = c;
                        updateStateFromSnapshot(snapshot);
                    }, e -> showError());
                } else {
                    updateStateFromSnapshot(snapshot);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        matchRef.child(roundKey).addValueEventListener(roundListener);
    }

    private void updateStateFromSnapshot(DataSnapshot snap) {
        String phase = snap.child("phase").getValue(String.class);
        String activePlayer = snap.child("player").getValue(String.class);

        isMyTurn = Objects.equals(myId, activePlayer);
        isOpponentChance = "opponent_chance".equals(phase);

        // Sync attempts
        DataSnapshot attsSnap = snap.child("attempts");
        int lastAtt = -1;
        for (DataSnapshot att : attsSnap.getChildren()) {
            int idx = Integer.parseInt(att.getKey());
            lastAtt = Math.max(lastAtt, idx);
            List<String> guess = (List<String>) att.getValue();
            if (guess != null) {
                for (int f = 0; f < 4; f++) tvAttemptFields[idx][f].setText(guess.get(f));
            }
        }
        attemptIndex = lastAtt + 1;

        // Sync results
        DataSnapshot resSnap = snap.child("results");
        for (DataSnapshot res : resSnap.getChildren()) {
            int idx = Integer.parseInt(res.getKey());
            Integer m = res.child("matches").getValue(Integer.class);
            Integer w = res.child("wrongPos").getValue(Integer.class);
            if (m != null && w != null) setFeedbackCircles(llAttemptFeedback[idx], m, w);
        }

        // Sync opponent chance
        if (isOpponentChance || snap.child("oppChanceGuess").exists()) {
            DataSnapshot ocSnap = snap.child("oppChanceGuess");
            if (ocSnap.exists()) {
                List<String> guess = (List<String>) ocSnap.getValue();
                if (guess != null) {
                    for (int f = 0; f < 4; f++) tvOppFields[f].setText(guess.get(f));
                }
                Boolean correct = snap.child("oppChanceCorrect").getValue(Boolean.class);
                if (correct != null) setFeedbackCircles(llOppFeedback, correct?4:0, 0);
            }
        }

        // FIX: bodovi runde se sad čitaju i prikazuju uživo (ranije se nikad nisu prikazivali)
        Integer myS = snap.child("scores").child(myId).getValue(Integer.class);
        Integer oppS = snap.child("scores").child(opponentId).getValue(Integer.class);
        myRoundScore = (myS != null) ? myS : 0;
        oppRoundScore = (oppS != null) ? oppS : 0;
        tvMyScore.setText(String.valueOf(myScoreTotal + myRoundScore));
        tvOpponentScore.setText(String.valueOf(oppScoreTotal + oppRoundScore));

        updateUIForTurn();

        if ("done".equals(phase)) {
            endRound();
        }
    }

    private void setupRoundUI(int round) {
        tvRound.setText("Runda " + round + "/2");
        resetGrid();
        currentGuess = new ArrayList<>(Arrays.asList("", "", "", ""));
        attemptIndex = 0;
        isOpponentChance = false;
        myRoundScore = 0;
        oppRoundScore = 0;
        updateScoreUI();
    }

    private void updateUIForTurn() {
        boolean enabled = isMyTurn;
        tvRound.setText("Runda " + currentRound + "/2 " + (isMyTurn ? "(Tvoj red)" : "(Čekaš...)"));
        btnFinish.setEnabled(enabled);
        btnDelete.setEnabled(enabled);
        setSymbolsEnabled(enabled);
        if (isMyTurn) startTimer(isOpponentChance ? 10 : 30);
    }

    private void onSymbolSelected(String sym) {
        if (!isMyTurn) return;
        for (int i = 0; i < 4; i++) {
            if (currentGuess.get(i).isEmpty()) {
                currentGuess.set(i, sym);
                if (isOpponentChance) tvOppFields[i].setText(sym);
                else tvAttemptFields[attemptIndex][i].setText(sym);
                break;
            }
        }
    }

    private void deleteLastSymbol() {
        if (!isMyTurn) return;
        for (int i = 3; i >= 0; i--) {
            if (!currentGuess.get(i).isEmpty()) {
                currentGuess.set(i, "");
                if (isOpponentChance) tvOppFields[i].setText("?");
                else tvAttemptFields[attemptIndex][i].setText("?");
                break;
            }
        }
    }

    private void submitGuess() {
        if (!isMyTurn) return;
        for (String s : currentGuess) if (s.isEmpty()) return;

        if (matchRef != null) {
            String rk = "round" + currentRound;
            if (isOpponentChance) {
                submitOpponentChance(rk, new ArrayList<>(currentGuess));
            } else {
                submitNormalAttempt(rk, new ArrayList<>(currentGuess));
            }
        } else {
            submitSoloGuess();
        }
        currentGuess = new ArrayList<>(Arrays.asList("", "", "", ""));
    }

    /** Jedan od 6 pokušaja glavnog "pogađača" u rundi. */
    private void submitNormalAttempt(String rk, List<String> guess) {
        List<String> solution = combo.getCombination();
        boolean correct = SkockoLogic.isCorrect(guess, solution);
        int m = SkockoLogic.countMatches(guess, solution);
        int w = SkockoLogic.countWrongPosition(guess, solution);
        int thisAttempt = attemptIndex;

        matchRef.child(rk).child("attempts").child(String.valueOf(thisAttempt)).setValue(guess)
                .addOnFailureListener(e -> showWriteError("attempts", e));
        Map<String, Integer> res = new HashMap<>();
        res.put("matches", m); res.put("wrongPos", w);
        matchRef.child(rk).child("results").child(String.valueOf(thisAttempt)).setValue(res)
                .addOnFailureListener(e -> showWriteError("results", e));

        if (correct) {
            int pts = SkockoLogic.pointsForAttempt(thisAttempt + 1);
            matchRef.child(rk).child("scores").child(myId).get().addOnSuccessListener(ds -> {
                int curr = ds.exists() ? ds.getValue(Integer.class) : 0;
                matchRef.child(rk).child("scores").child(myId).setValue(curr + pts)
                        .addOnFailureListener(e -> showWriteError("scores", e));
                matchRef.child(rk).child("phase").setValue("done")
                        .addOnFailureListener(e -> showWriteError("phase=done", e));
            });
        } else if (thisAttempt >= 5) {
            // 6 pokušaja iskorišćeno bez pogotka — protivnik dobija jedan pokušaj
            matchRef.child(rk).child("phase").setValue("opponent_chance")
                    .addOnFailureListener(e -> showWriteError("phase=oppChance", e));
            matchRef.child(rk).child("player").setValue(opponentId)
                    .addOnFailureListener(e -> showWriteError("player=opp", e));
        }
        // ako nije tačno i ima još pokušaja, ništa dodatno — attemptIndex se sam
        // osvežava u updateStateFromSnapshot() na osnovu broja upisanih attempts.
    }

    /** Bonus pokušaj protivnika (jedan jedini), posle 6 promašenih pokušaja glavnog igrača. */
    private void submitOpponentChance(String rk, List<String> guess) {
        List<String> solution = combo.getCombination();
        boolean correct = SkockoLogic.isCorrect(guess, solution);

        matchRef.child(rk).child("oppChanceGuess").setValue(guess)
                .addOnFailureListener(e -> showWriteError("oppChanceGuess", e));
        matchRef.child(rk).child("oppChanceCorrect").setValue(correct)
                .addOnFailureListener(e -> showWriteError("oppChanceCorrect", e));

        if (correct) {
            matchRef.child(rk).child("scores").child(myId).get().addOnSuccessListener(ds -> {
                int current = ds.exists() ? ds.getValue(Integer.class) : 0;
                matchRef.child(rk).child("scores").child(myId).setValue(current + SkockoLogic.PTS_OPPONENT)
                        .addOnFailureListener(e -> showWriteError("scores", e));
                matchRef.child(rk).child("phase").setValue("done")
                        .addOnFailureListener(e -> showWriteError("phase=done", e));
            });
        } else {
            matchRef.child(rk).child("phase").setValue("done")
                    .addOnFailureListener(e -> showWriteError("phase=done", e));
        }
    }

    /** Solo (gost) mod — bez Firebase-a, sve lokalno. */
    private void submitSoloGuess() {
        List<String> solution = combo.getCombination();
        boolean correct = SkockoLogic.isCorrect(currentGuess, solution);
        int m = SkockoLogic.countMatches(currentGuess, solution);
        int w = SkockoLogic.countWrongPosition(currentGuess, solution);

        setFeedbackCircles(llAttemptFeedback[attemptIndex], m, w);

        if (correct) {
            myScoreTotal += SkockoLogic.pointsForAttempt(attemptIndex + 1);
            updateScoreUI();
            endRoundSolo();
        } else if (attemptIndex >= 5) {
            endRoundSolo();
        } else {
            attemptIndex++;
        }
    }

    private void showWriteError(String what, Exception e) {
        Toast.makeText(this, "Write error (" + what + "): " + e.getMessage(),
                Toast.LENGTH_LONG).show();
    }

    private void endRound() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (roundListener != null) matchRef.child("round" + currentRound).removeEventListener(roundListener);
        roundListener = null;

        myScoreTotal += myRoundScore;
        oppScoreTotal += oppRoundScore;
        myRoundScore = 0;
        oppRoundScore = 0;
        updateScoreUI();

        if (currentRound == 1) {
            new Handler().postDelayed(() -> startMultiplayerRound(2), 2000);
        } else {
            statsRepo.saveSkockoResult(true, myScoreTotal, new StatsRepository.Callback<Void>() {
                @Override public void onSuccess(Void result) {}
                @Override public void onError(Exception e) {}
            });
            goNext();
        }
    }

    private void endRoundSolo() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        statsRepo.saveSkockoResult(true, myScoreTotal, new StatsRepository.Callback<Void>() {
            @Override public void onSuccess(Void result) {}
            @Override public void onError(Exception e) {}
        });
        showFinalScore();
    }

    private void startTimer(int seconds) {
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(seconds * 1000L, 1000) {
            @Override public void onTick(long ms) { tvTimer.setText(String.valueOf(ms / 1000)); }
            @Override public void onFinish() {
                tvTimer.setText("0");
                if (matchRef != null) {
                    if (isMyTurn) {
                        if (isOpponentChance) {
                            matchRef.child("round" + currentRound).child("phase").setValue("done")
                                    .addOnFailureListener(e -> showWriteError("phase=timeup", e));
                        } else {
                            // FIX: vreme isteklo a pokušaj nije potvrđen — beleži se kao
                            // promašen pokušaj (prazna polja se popunjavaju placeholder-om)
                            // i automatski se prelazi dalje, umesto da ostane zaglavljeno.
                            forceTimeoutAttempt();
                        }
                    }
                } else {
                    endRoundSolo();
                }
            }
        }.start();
    }

    private void forceTimeoutAttempt() {
        List<String> guess = new ArrayList<>(currentGuess);
        for (int i = 0; i < 4; i++) if (guess.get(i).isEmpty()) guess.set(i, "—");
        submitNormalAttempt("round" + currentRound, guess);
        currentGuess = new ArrayList<>(Arrays.asList("", "", "", ""));
    }

    private void resetGrid() {
        for (int i = 0; i < 6; i++) {
            for (int f = 0; f < 4; f++) {
                tvAttemptFields[i][f].setText("?");
                tvAttemptFields[i][f].setBackgroundColor(getColor(R.color.primary_green_light));
            }
            llAttemptFeedback[i].removeAllViews();
        }
        for (int f = 0; f < 4; f++) tvOppFields[f].setText("?");
        llOppFeedback.removeAllViews();
    }

    private void setFeedbackCircles(LinearLayout ll, int red, int yellow) {
        ll.removeAllViews();
        for (int i = 0; i < red; i++) ll.addView(makeCircle("#E53935"));
        for (int i = 0; i < yellow; i++) ll.addView(makeCircle("#FDD835"));
        for (int i = 0; i < (4 - red - yellow); i++) ll.addView(makeCircle("#CCCCCC"));
    }

    private View makeCircle(String color) {
        View v = new View(this);
        int s = (int)(12 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
        lp.setMargins(2, 0, 2, 0);
        v.setLayoutParams(lp);
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        d.setColor(android.graphics.Color.parseColor(color));
        v.setBackground(d);
        return v;
    }

    private void setSymbolsEnabled(boolean e) {
        for (TextView t : tvSymbolButtons) if (t != null) t.setEnabled(e);
    }

    private void showFinalScore() {
        new AlertDialog.Builder(this).setTitle("Kraj").setMessage("Bodovi: " + myScoreTotal).setPositiveButton("Dalje", (d, w) -> goNext()).show();
    }

    private void goNext() {
        Intent i = new Intent(this, KorakPoKorakActivity.class);
        i.putExtra("isGuest", isGuest); i.putExtra("matchId", matchId); i.putExtra("myId", myId);
        i.putExtra("opponentId", opponentId); i.putExtra("isPlayer1", isPlayer1);
        i.putExtra("totalMyScore", myScoreTotal); i.putExtra("totalOpponentScore", oppScoreTotal);
        startActivity(i); finish();
    }

    private void forfeit() {
        if (matchRef != null) matchRef.getParent().child("status").setValue("forfeit_" + myId);
        finish();
    }

    private void showError() { finish(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
        if (roundListener != null && matchRef != null) matchRef.child("round" + currentRound).removeEventListener(roundListener);
    }
}