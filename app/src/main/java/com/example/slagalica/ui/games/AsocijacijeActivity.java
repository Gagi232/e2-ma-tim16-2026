package com.example.slagalica.ui.games;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.slagalica.R;
import com.example.slagalica.data.model.Association;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.GameRepository;
import com.example.slagalica.data.repository.StatsRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.logic.AssociationLogic;
import com.example.slagalica.logic.LeagueLogic;
import com.example.slagalica.ui.main.GuestActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AsocijacijeActivity extends AppCompatActivity {

    // ── UI ───────────────────────────────────────────────────────────────────
    private TextView tvTimer, tvMyScore, tvOpponentScore, tvRound, tvFinalSolution;
    private TextView[][] tvFields;
    private TextView[] tvColSolution;
    private CardView cardFinalSolution;
    private boolean isFriendly;
    private TextInputEditText etAnswer;
    private MaterialButton btnPass, btnLeave;

    private MaterialButton btnGuessCol1, btnGuessCol2, btnGuessCol3, btnGuessCol4, btnGuessFinal;

    // ── Match State ──────────────────────────────────────────────────────────
    private String matchId, myId, opponentId;
    private boolean isPlayer1, isGuest;
    private DatabaseReference matchRef;

    private Association association;
    private int currentRound = 1;
    private int myScoreTotal = 0;
    private int oppScoreTotal = 0;

    private boolean[] colSolved = new boolean[4];
    private boolean[] fieldRevealed = new boolean[16];
    private boolean finalSolved = false;
    private boolean isMyTurn = false;
    private boolean hasOpenedInTurn = false;
    private String currentTurnId;

    // NOVO: eksplicitno pratimo za koju rundu je UI / tajmer već inicijalizovan,
    // umesto da to pogađamo iz identiteta `association` objekta ili `timer == null`.
    // Ranije: igrač koji POKREĆE rundu već lokalno postavi `association` PRE nego
    // što mu stigne echo iz Firebase-a, pa je kod mislio da je runda "već učitana"
    // i preskakao setupRoundUI() (nema click listenera na poljima) i startSyncedTimer()
    // (jer timer nikad nije vraćan na null posle cancel()).
    private int uiSetupRound = -1;
    private int timerStartedRound = -1;

    private int activeGuessTarget = -1;
    private CountDownTimer timer;
    private static final int ROUND_SECS = 240; // Spec: 4 mins total for 2 rounds? (2*2min)

    private final GameRepository repo = new GameRepository();
    private final StatsRepository statsRepo = new StatsRepository();
    private final UserRepository userRepo = new UserRepository();
    private ValueEventListener roundListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_asocijacije);

        isGuest    = getIntent().getBooleanExtra("isGuest", false);
        matchId    = getIntent().getStringExtra("matchId");
        myId       = getIntent().getStringExtra("myId");
        opponentId = getIntent().getStringExtra("opponentId");
        isPlayer1  = getIntent().getBooleanExtra("isPlayer1", true);
        isFriendly = getIntent().getBooleanExtra("isFriendly", false);
        myScoreTotal  = getIntent().getIntExtra("totalMyScore", 0);
        oppScoreTotal = getIntent().getIntExtra("totalOpponentScore", 0);

        bindViews();
        updateTopBar();
        updateScoreUI();

        if (isGuest || matchId == null) {
            loadSolo();
        } else {
            matchRef = FirebaseDatabase.getInstance().getReference("activeMatches").child(matchId).child("asocijacije");
            listenForForfeit();
            startMultiplayerRound(1);
        }
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

    private void updateScoreUI() {
        tvMyScore.setText(String.valueOf(myScoreTotal));
        tvOpponentScore.setText(String.valueOf(oppScoreTotal));
    }

    private void bindViews() {
        tvTimer         = findViewById(R.id.tvTimer);
        tvMyScore       = findViewById(R.id.tvMyScore);
        tvOpponentScore = findViewById(R.id.tvOpponentScore);
        tvRound         = findViewById(R.id.tvRound);
        tvFinalSolution = findViewById(R.id.tvFinalSolution);
        cardFinalSolution = findViewById(R.id.cardFinalSolution);
        etAnswer        = findViewById(R.id.etAnswer);
        btnPass         = findViewById(R.id.btnFinish); // Using btnFinish for Pass
        btnLeave        = findViewById(R.id.btnLeave);

        btnPass.setText("Sledeći");
        btnPass.setVisibility(View.GONE);

        btnGuessCol1  = findViewById(R.id.btnGuessCol1);
        btnGuessCol2  = findViewById(R.id.btnGuessCol2);
        btnGuessCol3  = findViewById(R.id.btnGuessCol3);
        btnGuessCol4  = findViewById(R.id.btnGuessCol4);
        btnGuessFinal = findViewById(R.id.btnGuessFinal);

        int[][] fieldIds = {
                { R.id.tvCol1Field1, R.id.tvCol1Field2, R.id.tvCol1Field3, R.id.tvCol1Field4 },
                { R.id.tvCol2Field1, R.id.tvCol2Field2, R.id.tvCol2Field3, R.id.tvCol2Field4 },
                { R.id.tvCol3Field1, R.id.tvCol3Field2, R.id.tvCol3Field3, R.id.tvCol3Field4 },
                { R.id.tvCol4Field1, R.id.tvCol4Field2, R.id.tvCol4Field3, R.id.tvCol4Field4 },
        };
        tvFields = new TextView[4][4];
        for (int c = 0; c < 4; c++)
            for (int f = 0; f < 4; f++)
                tvFields[c][f] = findViewById(fieldIds[c][f]);

        int[] solIds = {
                R.id.tvCol1Solution, R.id.tvCol2Solution,
                R.id.tvCol3Solution, R.id.tvCol4Solution
        };
        tvColSolution = new TextView[4];
        for (int c = 0; c < 4; c++) tvColSolution[c] = findViewById(solIds[c]);

        btnGuessCol1.setOnClickListener(v -> selectGuessTarget(0));
        btnGuessCol2.setOnClickListener(v -> selectGuessTarget(1));
        btnGuessCol3.setOnClickListener(v -> selectGuessTarget(2));
        btnGuessCol4.setOnClickListener(v -> selectGuessTarget(3));
        btnGuessFinal.setOnClickListener(v -> selectGuessTarget(4));

        etAnswer.setOnEditorActionListener((v, actionId, event) -> {
            submitAnswer();
            return true;
        });

        btnPass.setOnClickListener(v -> switchTurn());
        btnLeave.setOnClickListener(v -> forfeit());
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

        userRepo.getCurrentUser(new UserRepository.Callback<User>() {
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


    private void loadSolo() {
        repo.getRandomAssociation(assoc -> {
            association = assoc;
            setupRoundUI(1);
            isMyTurn = true;
            startTimer(120); // 2 min per round
        }, e -> showError());
    }

    private void startMultiplayerRound(int round) {
        currentRound = round;
        String roundKey = "round" + round;
        boolean iStart = (round == 1) ? isPlayer1 : !isPlayer1;

        if (iStart) {
            repo.getRandomAssociation(assoc -> {
                association = assoc;
                Map<String, Object> data = new HashMap<>();
                data.put("setId", assoc.getId());
                data.put("turn", myId);
                data.put("phase", "playing");
                data.put("startedAt", com.google.firebase.database.ServerValue.TIMESTAMP);
                // Briše STARI sadržaj ovog round-a (revealed, scores, solvedCols, turn...) prije nego
                // upiše nov — sprečava da leftover podaci iz prethodnih test-mečeva blokiraju novu rundu.
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

                // FIX: UI (grid + click listeneri) se inicijalizuje TAČNO JEDNOM po rundi,
                // na osnovu broja runde, a ne na osnovu toga da li je `association` već
                // lokalno postavljen. Igrač koji pokreće rundu već ima `association`
                // postavljen pre nego što mu stigne echo iz baze, pa stara provera
                // (`association == null || !id.equals(setId)`) nikad nije bila tačna za njega
                // i setupRoundUI() (gde se kače click listeneri na polja) se nikad nije zvao.
                if (uiSetupRound != currentRound) {
                    uiSetupRound = currentRound;
                    setupRoundUI(currentRound);
                }

                if (association == null || !association.getId().equals(setId)) {
                    repo.getAssociationById(setId, assoc -> {
                        association = assoc;
                        maybeStartTimer(snapshot);
                        updateStateFromSnapshot(snapshot);
                    }, e -> showError());
                } else {
                    maybeStartTimer(snapshot);
                    updateStateFromSnapshot(snapshot);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        matchRef.child(roundKey).addValueEventListener(roundListener);
    }

    /**
     * FIX: tajmer se startuje tačno jednom po rundi (prema broju runde), a ne na osnovu
     * `timer == null`. `timer.cancel()` u endRound() ne postavlja `timer` na null, pa je
     * stari uslov bio false za svaku rundu posle prve — zbog toga tajmer nije radio kod
     * igrača koji počinje rundu 2 (i sve naredne runde).
     */
    private void maybeStartTimer(DataSnapshot snap) {
        if (timerStartedRound != currentRound) {
            timerStartedRound = currentRound;
            startSyncedTimer(snap);
        }
    }

    private void updateStateFromSnapshot(DataSnapshot snap) {
        currentTurnId = snap.child("turn").getValue(String.class);
        boolean wasMyTurn = isMyTurn;
        isMyTurn = Objects.equals(myId, currentTurnId);

        if (isMyTurn && !wasMyTurn) {
            hasOpenedInTurn = false;
        }

        // Revealed fields
        for (int i = 0; i < 16; i++) {
            boolean revealed = snap.child("revealed").child(String.valueOf(i)).exists();
            if (revealed && !fieldRevealed[i]) {
                fieldRevealed[i] = true;
                int c = i / 4, f = i % 4;
                tvFields[c][f].setText(getWord(c, f));
                tvFields[c][f].setBackgroundColor(getColor(R.color.primary_green_dark));
            }
        }

        // Solved columns
        for (int i = 0; i < 4; i++) {
            DataSnapshot colSnap = snap.child("solvedCols").child(String.valueOf(i));
            if (colSnap.exists() && !colSolved[i]) {
                colSolved[i] = true;
                tvColSolution[i].setText(getColSolution(i));
                // Reveal all fields in solved column
                for (int f = 0; f < 4; f++) {
                    int idx = i * 4 + f;
                    fieldRevealed[idx] = true;
                    tvFields[i][f].setText(getWord(i, f));
                }
            }
        }

        // Final solution
        DataSnapshot finalSnap = snap.child("finalSolved");
        if (finalSnap.exists() && !finalSolved) {
            finalSolved = true;
            tvFinalSolution.setText(association.getFinalSolution());
            revealAll();
        }

        // Scores
        Integer myS = snap.child("scores").child(myId).getValue(Integer.class);
        Integer oppS = snap.child("scores").child(opponentId).getValue(Integer.class);
        int myRound = (myS != null) ? myS : 0;
        int oppRound = (oppS != null) ? oppS : 0;
        tvMyScore.setText(String.valueOf(myScoreTotal + myRound));
        tvOpponentScore.setText(String.valueOf(oppScoreTotal + oppRound));

        updateUIForTurn();

        String phase = snap.child("phase").getValue(String.class);
        if ("done".equals(phase)) {
            endRound(myRound, oppRound);
        }
    }

    private void startSyncedTimer(DataSnapshot snap) {
        Long startedAt = snap.child("startedAt").getValue(Long.class);
        long elapsed = (startedAt != null) ? (System.currentTimeMillis() - startedAt) : 0;
        long remainingMs = AssociationLogic.ROUND_TIME_MS - elapsed;
        if (remainingMs < 0) remainingMs = 0;
        startTimer((int) (remainingMs / 1000));
    }

    private void setupRoundUI(int round) {
        tvRound.setText("Runda " + round + "/2");
        colSolved = new boolean[4];
        fieldRevealed = new boolean[16];
        finalSolved = false;
        hasOpenedInTurn = false;
        resetGrid();
        // Timer se pokreće odvojeno u startSyncedTimer(), na osnovu serverskog "startedAt"
    }

    private void resetGrid() {
        for (int c = 0; c < 4; c++) {
            for (int f = 0; f < 4; f++) {
                tvFields[c][f].setText("?");
                tvFields[c][f].setBackgroundColor(getColor(R.color.primary_green));
                final int col = c, field = f;
                tvFields[c][f].setOnClickListener(v -> onFieldClick(col, field));
            }
            tvColSolution[c].setText("?");
        }
        tvFinalSolution.setText("KONAČNO REŠENJE");
        etAnswer.setText("");
        setGuessButtonsVisible(false);
    }

    private void updateUIForTurn() {
        boolean enabled = isMyTurn && !finalSolved;
        tvRound.setText("Runda " + currentRound + "/2 " + (isMyTurn ? "(Tvoj red)" : "(Čekaš...)"));
        etAnswer.setEnabled(enabled);
        btnPass.setVisibility(enabled && hasOpenedInTurn ? View.VISIBLE : View.GONE);
        setGuessButtonsVisible(enabled && hasOpenedInTurn);

        for (int c = 0; c < 4; c++)
            for (int f = 0; f < 4; f++)
                tvFields[c][f].setClickable(enabled && !hasOpenedInTurn && !fieldRevealed[c*4+f]);
    }

    private void onFieldClick(int col, int field) {
        if (!isMyTurn || finalSolved || hasOpenedInTurn) return;
        int idx = col * 4 + field;
        if (fieldRevealed[idx]) return;

        hasOpenedInTurn = true;
        if (matchRef != null) {
            matchRef.child("round" + currentRound).child("revealed").child(String.valueOf(idx)).setValue(true)
                    .addOnFailureListener(e -> {
                        // Write nije uspio — vrati lokalni flag da korisnik može da pokuša ponovo
                        hasOpenedInTurn = false;
                        showWriteError("revealed", e);
                        updateUIForTurn();
                    });
        } else {
            fieldRevealed[idx] = true;
            tvFields[col][field].setText(getWord(col, field));
            tvFields[col][field].setBackgroundColor(getColor(R.color.primary_green_dark));
            updateUIForTurn();
        }
    }

    private void selectGuessTarget(int target) {
        if (target < 4 && colSolved[target]) return;
        activeGuessTarget = target;
        etAnswer.setHint(target < 4 ? "Rešenje kolone " + (char)('A'+target) : "Konačno rešenje");
        etAnswer.requestFocus();
    }

    private void submitAnswer() {
        if (!isMyTurn || activeGuessTarget == -1) return;
        String answer = etAnswer.getText().toString().trim();
        if (answer.isEmpty()) return;

        if (activeGuessTarget < 4) {
            if (answer.equalsIgnoreCase(getColSolution(activeGuessTarget))) {
                handleCorrectCol(activeGuessTarget);
            } else {
                switchTurn();
            }
        } else {
            if (answer.equalsIgnoreCase(association.getFinalSolution())) {
                handleCorrectFinal();
            } else {
                switchTurn();
            }
        }
        etAnswer.setText("");
        activeGuessTarget = -1;
    }

    private void handleCorrectCol(int col) {
        if (matchRef != null) {
            String rk = "round" + currentRound;
            matchRef.child(rk).child("solvedCols").child(String.valueOf(col)).setValue(myId)
                    .addOnFailureListener(e -> showWriteError("solvedCols", e));

            int hidden = 0;
            for (int f = 0; f < 4; f++) if (!fieldRevealed[col*4+f]) hidden++;
            int pts = AssociationLogic.colScore(hidden);

            matchRef.child(rk).child("scores").child(myId)
                    .runTransaction(new com.google.firebase.database.Transaction.Handler() {
                        @Override
                        public com.google.firebase.database.Transaction.Result doTransaction(
                                com.google.firebase.database.MutableData data) {
                            Integer cur = data.getValue(Integer.class);
                            data.setValue((cur == null ? 0 : cur) + pts);
                            return com.google.firebase.database.Transaction.success(data);
                        }
                        @Override
                        public void onComplete(DatabaseError error, boolean committed,
                                               DataSnapshot snap) {
                            if (error != null) showWriteError("scores(col)", error.toException());
                        }
                    });
        } else {
            colSolved[col] = true;
            tvColSolution[col].setText(getColSolution(col));
            myScoreTotal += AssociationLogic.colScore(0);
            updateScoreUI();
            // In solo, keep turn if correct
        }
    }
    private void handleCorrectFinal() {
        if (matchRef != null) {
            String rk = "round" + currentRound;
            int[] hiddenPerCol = new int[4];
            for (int c = 0; c < 4; c++) {
                for (int f = 0; f < 4; f++) if (!fieldRevealed[c*4+f]) hiddenPerCol[c]++;
            }
            int pts = AssociationLogic.finalScore(colSolved, hiddenPerCol);

            matchRef.child(rk).child("scores").child(myId)
                    .runTransaction(new com.google.firebase.database.Transaction.Handler() {
                        @Override
                        public com.google.firebase.database.Transaction.Result doTransaction(
                                com.google.firebase.database.MutableData data) {
                            Integer cur = data.getValue(Integer.class);
                            data.setValue((cur == null ? 0 : cur) + pts);
                            return com.google.firebase.database.Transaction.success(data);
                        }
                        @Override
                        public void onComplete(DatabaseError error, boolean committed,
                                               DataSnapshot snap) {
                            if (error != null) {
                                showWriteError("scores(final)", error.toException());
                                return;
                            }
                            // Tek SADA, kad su bodovi sigurno upisani, javi finalSolved i done
                            matchRef.child(rk).child("finalSolved").setValue(myId)
                                    .addOnFailureListener(e -> showWriteError("finalSolved", e));
                            matchRef.child(rk).child("phase").setValue("done")
                                    .addOnFailureListener(e -> showWriteError("phase=done", e));
                        }
                    });
        } else {
            finalSolved = true;
            int[] hiddenPerCol = new int[4];
            for (int c = 0; c < 4; c++)
                for (int f = 0; f < 4; f++) if (!fieldRevealed[c*4+f]) hiddenPerCol[c]++;
            int pts = AssociationLogic.finalScore(colSolved, hiddenPerCol);
            myScoreTotal += pts;
            updateScoreUI();
            tvFinalSolution.setText(association.getFinalSolution());
            revealAll();
            endRound(0, 0);
        }
    }

    private void showWriteError(String what, Exception e) {
        Toast.makeText(this, "Write error (" + what + "): " + e.getMessage(),
                Toast.LENGTH_LONG).show();
    }
    private void switchTurn() {
        hasOpenedInTurn = false;
        if (matchRef != null) {
            matchRef.child("round" + currentRound).child("turn").setValue(opponentId);
        } else {
            // Solo: just reset opening flag or end?
            isMyTurn = true;
            updateUIForTurn();
        }
    }

    private void endRound(int myRoundPts, int oppRoundPts) {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (roundListener != null) matchRef.child("round" + currentRound).removeEventListener(roundListener);
        roundListener = null;

        myScoreTotal += myRoundPts;
        oppScoreTotal += oppRoundPts;

        if (currentRound == 1) {
            if (matchRef == null) {
                // solo/challenge — učitaj novu asocijaciju za rundu 2
                new Handler().postDelayed(() -> {
                    currentRound = 2;
                    repo.getRandomAssociation(assoc -> {
                        association = assoc;
                        uiSetupRound = -1;
                        timerStartedRound = -1;
                        setupRoundUI(2);
                        isMyTurn = true;
                        startTimer(120);
                    }, e -> showError());
                }, 2000);
            } else {
                new Handler().postDelayed(() -> startMultiplayerRound(2), 2000);
            }
        } else {
            statsRepo.saveAsocijacijeResult(finalSolved, myRoundPts, new StatsRepository.Callback<Void>() {
                @Override public void onSuccess(Void r) {}
                @Override public void onError(Exception e) {}
            });
            goNext();
        }
    }

    private void startTimer(int seconds) {
        if (timer != null) timer.cancel();
        if (seconds <= 0) {
            tvTimer.setText("0");
            onRoundTimeUp();
            return;
        }
        timer = new CountDownTimer(seconds * 1000L, 1000) {
            @Override public void onTick(long ms) { tvTimer.setText(String.valueOf(ms / 1000)); }
            @Override public void onFinish() {
                tvTimer.setText("0");
                onRoundTimeUp();
            }
        }.start();
    }

    /** Bilo koji od igrača (ne samo onaj kome je red) može da javi da je vreme isteklo. */
    private void onRoundTimeUp() {
        if (matchRef != null) {
            String rk = "round" + currentRound;
            matchRef.child(rk).child("phase").get().addOnSuccessListener(ds -> {
                String phase = ds.getValue(String.class);
                if (!"done".equals(phase)) {
                    matchRef.child(rk).child("phase").setValue("done")
                            .addOnFailureListener(e -> showWriteError("phase=timeup", e));
                }
            });
        } else if (isGuest || matchId == null) {
            endRound(0, 0);
        }
    }

    private void revealAll() {
        for (int c = 0; c < 4; c++) {
            tvColSolution[c].setText(getColSolution(c));
            for (int f = 0; f < 4; f++) tvFields[c][f].setText(getWord(c, f));
        }
        tvFinalSolution.setText(association.getFinalSolution());
    }

    private String getWord(int col, int field) {
        List<String> list = (col==0)?association.getCol1():(col==1)?association.getCol2():(col==2)?association.getCol3():association.getCol4();
        return (list != null && field < list.size()) ? list.get(field) : "";
    }

    private String getColSolution(int col) {
        return (col==0)?association.getCol1Solution():(col==1)?association.getCol2Solution():(col==2)?association.getCol3Solution():association.getCol4Solution();
    }

    private void setGuessButtonsVisible(boolean visible) {
        int v = visible ? View.VISIBLE : View.GONE;
        btnGuessCol1.setVisibility(v); btnGuessCol2.setVisibility(v); btnGuessCol3.setVisibility(v); btnGuessCol4.setVisibility(v); btnGuessFinal.setVisibility(v);
    }

    private void showFinalScore() {
        new AlertDialog.Builder(this).setTitle("Kraj").setMessage("Bodovi: " + (myScoreTotal)).setPositiveButton("Dalje", (d, w) -> goNext()).show();
    }

    private void goNext() {
        boolean isChallengeMode = getIntent().getBooleanExtra("isChallengeMode", false);
        if (isChallengeMode) {
            Intent result = new Intent();
            result.putExtra("gameScore", myScoreTotal);
            setResult(RESULT_OK, result);
            finish();
            return;
        }

        Intent intent = new Intent(this, SkockoActivity.class);
        intent.putExtra("isGuest", isGuest);
        intent.putExtra("matchId", matchId);
        intent.putExtra("myId", myId);
        intent.putExtra("opponentId", opponentId);
        intent.putExtra("isPlayer1", isPlayer1);
        intent.putExtra("isFriendly", isFriendly);
        intent.putExtra("totalMyScore", myScoreTotal);
        intent.putExtra("totalOpponentScore", oppScoreTotal);
        startActivity(intent);
        finish();
    }

    private void forfeit() {
        // otkazi sve lokalne tajmere/listenere specificne za ovu igru (vec postoji kod za to)
        if (timer != null) timer.cancel();
        if (matchRef != null) matchRef.getParent().child("info").child("status").setValue("forfeit_" + myId);
        removeForfeitListener();
        goToResultsAsLoser();
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

    private void handleOpponentForfeit() {
        if (timer != null) timer.cancel();
        removeForfeitListener();
        Intent intent = new Intent(this, com.example.slagalica.ui.main.ResultsActivity.class);
        intent.putExtra("isGuest", isGuest);
        intent.putExtra("isFriendly", isFriendly);
        intent.putExtra("totalMyScore", myScoreTotal);
        intent.putExtra("totalOpponentScore", 0);
        startActivity(intent);
        finish();
    }

    private void showError() {
        Toast.makeText(this, "Greška!", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeForfeitListener();
        if (timer != null) timer.cancel();
        if (roundListener != null && matchRef != null) matchRef.child("round" + currentRound).removeEventListener(roundListener);
    }
}