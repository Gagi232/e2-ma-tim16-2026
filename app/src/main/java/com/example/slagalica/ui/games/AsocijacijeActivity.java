package com.example.slagalica.ui.games;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.slagalica.R;
import com.example.slagalica.data.model.Association;
import com.example.slagalica.data.repository.GameRepository;
import com.example.slagalica.logic.AssociationLogic;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

public class AsocijacijeActivity extends AppCompatActivity {

    // ── UI ───────────────────────────────────────────────────────────────────
    private TextView tvTimer, tvMyScore, tvOpponentScore, tvRound, tvFinalSolution;
    private TextView[][] tvFields;
    private TextView[] tvColSolution;
    private CardView cardFinalSolution;
    private TextInputEditText etAnswer;
    private MaterialButton btnFinish, btnLeave;

    // Dugmici za odabir sta se pogadja
    private MaterialButton btnGuessCol1, btnGuessCol2, btnGuessCol3, btnGuessCol4, btnGuessFinal;

    // ── State ────────────────────────────────────────────────────────────────
    private Association association;
    private int currentRound = 1;
    private int myScore = 0;
    private int opponentScore = 0;

    private boolean[] colSolved;
    private int[] hiddenFields;
    private boolean[] fieldRevealed;
    private boolean finalSolved;
    private boolean isMyTurn;

    // Koji col/final trenutno pogadjamo (-1 = nista nije selektovano, 0-3 = kolona, 4 = final)
    private int activeGuessTarget = -1;

    private CountDownTimer timer;
    private int timeLeft;

    private static final int ROUND_SECS = 120;

    private final GameRepository repo = new GameRepository();

    private boolean waitingForGuess = false;

    // ────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_asocijacije);
        bindViews();
        loadAssociationAndStartRound(1);
    }

    // ── Bind ─────────────────────────────────────────────────────────────────
    private void bindViews() {
        tvTimer         = findViewById(R.id.tvTimer);
        tvMyScore       = findViewById(R.id.tvMyScore);
        tvOpponentScore = findViewById(R.id.tvOpponentScore);
        tvRound         = findViewById(R.id.tvRound);
        tvFinalSolution = findViewById(R.id.tvFinalSolution);
        cardFinalSolution = findViewById(R.id.cardFinalSolution);
        etAnswer        = findViewById(R.id.etAnswer);
        btnFinish       = findViewById(R.id.btnFinish);
        btnLeave        = findViewById(R.id.btnLeave);

        // Guess target buttons — dodaj ove u layout (vidi napomenu ispod)
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

        // Guess target button listeners
        btnGuessCol1.setOnClickListener(v -> selectGuessTarget(0));
        btnGuessCol2.setOnClickListener(v -> selectGuessTarget(1));
        btnGuessCol3.setOnClickListener(v -> selectGuessTarget(2));
        btnGuessCol4.setOnClickListener(v -> selectGuessTarget(3));
        btnGuessFinal.setOnClickListener(v -> selectGuessTarget(4));

        // Potvrda odgovora iz etAnswer
        etAnswer.setOnEditorActionListener((v, actionId, event) -> {
            submitAnswer();
            return true;
        });

        btnFinish.setOnClickListener(v -> goNext());
        btnLeave.setOnClickListener(v -> confirmLeave());
    }

    // ── Load & start round ────────────────────────────────────────────────────
    private void loadAssociationAndStartRound(int round) {
        repo.getRandomAssociation(
                assoc -> {
                    association = assoc;
                    startRound(round);
                },
                e -> Toast.makeText(this, "Greška pri učitavanju asocijacije.", Toast.LENGTH_SHORT).show()
        );
    }

    // ── Round lifecycle ───────────────────────────────────────────────────────
    private void startRound(int round) {
        currentRound  = round;
        isMyTurn      = (round == 1);
        activeGuessTarget = -1;

        colSolved     = new boolean[4];
        hiddenFields  = new int[]{4, 4, 4, 4};
        fieldRevealed = new boolean[16];
        finalSolved   = false;

        tvRound.setText("Runda " + round + "/2");
        updateScoreBar();
        resetGrid();
        updateGuessButtonStates();

        if (isMyTurn) {
            enableInput(true);
            startTimer(ROUND_SECS);
        } else {
            enableInput(false);
            simulateOpponentRound();
        }
    }

    private void resetGrid() {
        for (int c = 0; c < 4; c++) {
            for (int f = 0; f < 4; f++) {
                TextView tv = tvFields[c][f];
                tv.setText("?");
                tv.setBackgroundColor(getColor(R.color.primary_green));
                final int col = c, field = f;
                tv.setOnClickListener(v -> onFieldClick(col, field));
            }
            // Eksplicitno sakrij solution redak
            tvColSolution[c].setText("?");
            colSolved[c] = false;
        }
        waitingForGuess = false;
        cardFinalSolution.setVisibility(View.VISIBLE);
        tvFinalSolution.setText("KONAČNO REŠENJE");
        etAnswer.setText("");
        setGuessButtonsVisible(false);
    }

    // ── Field click ───────────────────────────────────────────────────────────
    private void onFieldClick(int col, int field) {
        if (!isMyTurn) return;
        if (waitingForGuess) {
            Toast.makeText(this, "Prvo unesi odgovor!", Toast.LENGTH_SHORT).show();
            return;
        }
        int idx = col * 4 + field;
        if (fieldRevealed[idx]) return;

        fieldRevealed[idx] = true;
        hiddenFields[col]--;
        tvFields[col][field].setText(getWord(col, field));
        tvFields[col][field].setBackgroundColor(getColor(R.color.primary_green_dark));

        waitingForGuess = true;
        setGuessButtonsVisible(true);
        updateGuessButtonStates();
        etAnswer.setText("");
        etAnswer.requestFocus();
    }

    // ── Guess target selection ─────────────────────────────────────────────────
    private void selectGuessTarget(int target) {
        // target: 0-3 = kolona, 4 = final
        if (target < 4 && colSolved[target]) {
            Toast.makeText(this, "Ta kolona je već pogođena.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (target == 4 && finalSolved) {
            Toast.makeText(this, "Konačno rešenje je već pogođeno.", Toast.LENGTH_SHORT).show();
            return;
        }
        activeGuessTarget = target;
        updateGuessButtonStates();

        // Postavi hint u polje
        if (target < 4) {
            etAnswer.setHint("Rešenje kolone " + (target + 1));
        } else {
            etAnswer.setHint("Konačno rešenje");
        }
        etAnswer.setText("");
        etAnswer.requestFocus();
    }

    private void updateGuessButtonStates() {
        MaterialButton[] colBtns = {btnGuessCol1, btnGuessCol2, btnGuessCol3, btnGuessCol4};
        for (int c = 0; c < 4; c++) {
            colBtns[c].setAlpha(colSolved[c] ? 0.4f : 1.0f);
            colBtns[c].setSelected(activeGuessTarget == c);
        }
        btnGuessFinal.setAlpha(finalSolved ? 0.4f : 1.0f);
        btnGuessFinal.setSelected(activeGuessTarget == 4);
    }

    private void setGuessButtonsVisible(boolean visible) {
        int vis = visible ? View.VISIBLE : View.GONE;
        btnGuessCol1.setVisibility(vis);
        btnGuessCol2.setVisibility(vis);
        btnGuessCol3.setVisibility(vis);
        btnGuessCol4.setVisibility(vis);
        btnGuessFinal.setVisibility(vis);
    }

    // ── Submit answer ─────────────────────────────────────────────────────────
    private void submitAnswer() {
        if (activeGuessTarget == -1) {
            Toast.makeText(this, "Odaberi šta pogađaš.", Toast.LENGTH_SHORT).show();
            return;
        }
        String answer = etAnswer.getText() != null ? etAnswer.getText().toString().trim() : "";
        if (answer.isEmpty()) return;

        if (activeGuessTarget < 4) {
            checkColGuess(activeGuessTarget, answer);
        } else {
            checkFinalGuess(answer);
        }
        etAnswer.setText("");
        activeGuessTarget = -1;
        waitingForGuess = false;
        setGuessButtonsVisible(false);
        updateGuessButtonStates();
    }

    private void checkColGuess(int col, String answer) {
        if (answer.equalsIgnoreCase(getColSolution(col))) {
            colSolved[col] = true;
            tvColSolution[col].setText(getColSolution(col));
            int pts = AssociationLogic.colScore(hiddenFields[col]);
            myScore += pts;
            updateScoreBar();
            updateGuessButtonStates();
            Toast.makeText(this, "Tačno! +" + pts + " bodova", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Netačno.", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkFinalGuess(String answer) {
        if (answer.equalsIgnoreCase(association.getFinalSolution())) {
            finalSolved = true;
            int pts = AssociationLogic.finalScore(colSolved, hiddenFields);
            myScore += pts;
            updateScoreBar();
            revealAll();
            Toast.makeText(this, "Tačno! +" + pts + " bodova", Toast.LENGTH_SHORT).show();
            endRoundForPlayer();
        } else {
            Toast.makeText(this, "Netačno.", Toast.LENGTH_SHORT).show();
        }
    }

    // ── Timer ─────────────────────────────────────────────────────────────────
    private void startTimer(int seconds) {
        timeLeft = seconds;
        tvTimer.setText(String.valueOf(timeLeft));
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(seconds * 1000L, 1000) {
            @Override public void onTick(long ms) {
                timeLeft = (int)(ms / 1000);
                tvTimer.setText(String.valueOf(timeLeft));
            }
            @Override public void onFinish() {
                tvTimer.setText("0");
                onRoundTimeUp();
            }
        }.start();
    }

    private void onRoundTimeUp() {
        if (isMyTurn) {
            revealAll();
            endRoundForPlayer();
        }
    }

    private void endRoundForPlayer() {
        if (timer != null) timer.cancel();
        enableInput(false);
        setGuessButtonsVisible(false);
        waitingForGuess = false;
        revealAll();

        if (currentRound == 1) {
            tvRound.postDelayed(() -> {
                // Tek nakon delay-a ucitaj novu asocijaciju
                repo.getRandomAssociation(
                        assoc -> {
                            association = assoc;
                            // Sada kada imamo novu asocijaciju, resetuj i kreni rundu 2
                            currentRound = 2;
                            isMyTurn = true; // direktno igrac igra, bez simulate
                            activeGuessTarget = -1;
                            colSolved     = new boolean[4];
                            hiddenFields  = new int[]{4, 4, 4, 4};
                            fieldRevealed = new boolean[16];
                            finalSolved   = false;
                            tvRound.setText("Runda 2/2");
                            updateScoreBar();
                            resetGrid();
                            enableInput(true);
                            startTimer(ROUND_SECS);
                        },
                        e -> Toast.makeText(this, "Greška pri učitavanju asocijacije.", Toast.LENGTH_SHORT).show()
                );
            }, 1500);
        } else {
            showFinalScore();
        }
    }

    // ── Opponent simulation ───────────────────────────────────────────────────
    private void simulateOpponentRound() {
        // Izracunaj bodove protivnika u pozadini, bez ikakvih UI promena na gridu
        int oppPts = 0;
        java.util.Random rnd = new java.util.Random();

        for (int c = 0; c < 4; c++) {
            if (rnd.nextBoolean()) {
                int hidden = rnd.nextInt(4);
                oppPts += AssociationLogic.colScore(hidden);
            }
        }
        final int finalOppPts = oppPts;
        tvTimer.setText("--");

        tvRound.postDelayed(() -> {
            opponentScore += finalOppPts;
            updateScoreBar();
            Toast.makeText(this, "Protivnik odigrao. Sada ti ideš!", Toast.LENGTH_SHORT).show();
            isMyTurn = true;
            enableInput(true);
            startTimer(ROUND_SECS);
        }, 2000);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private String getWord(int col, int field) {
        List<String> list;
        switch (col) {
            case 0: list = association.getCol1(); break;
            case 1: list = association.getCol2(); break;
            case 2: list = association.getCol3(); break;
            default: list = association.getCol4(); break;
        }
        return (list != null && field < list.size()) ? list.get(field) : "?";
    }

    private String getColSolution(int col) {
        switch (col) {
            case 0: return association.getCol1Solution();
            case 1: return association.getCol2Solution();
            case 2: return association.getCol3Solution();
            default: return association.getCol4Solution();
        }
    }

    private void revealAll() {
        for (int c = 0; c < 4; c++) {
            tvColSolution[c].setText(getColSolution(c));
            for (int f = 0; f < 4; f++) {
                tvFields[c][f].setText(getWord(c, f));
                tvFields[c][f].setOnClickListener(null);
            }
        }
        tvFinalSolution.setText(association.getFinalSolution());
    }

    private void enableInput(boolean enabled) {
        etAnswer.setEnabled(enabled);
        for (int c = 0; c < 4; c++)
            for (int f = 0; f < 4; f++)
                tvFields[c][f].setClickable(enabled);
    }

    private void updateScoreBar() {
        tvMyScore.setText(String.valueOf(myScore));
        tvOpponentScore.setText(String.valueOf(opponentScore));
    }

    private void showFinalScore() {
        revealAll();
        new AlertDialog.Builder(this)
                .setTitle("Asocijacije završene")
                .setMessage("Tvoji bodovi: " + myScore + "\nProtivnik: " + opponentScore)
                .setCancelable(false)
                .setPositiveButton("Dalje", (d, w) -> goNext())
                .show();
    }

    private void goNext() {
        if (timer != null) timer.cancel();

        // Uzmi skore iz prethdnog
        int prevMy  = getIntent().getIntExtra("totalMyScore", 0);
        int prevOpp = getIntent().getIntExtra("totalOpponentScore", 0);

        Intent intent = new Intent(this, SkockoActivity.class);
        intent.putExtra("isGuest",            getIntent().getBooleanExtra("isGuest", false));
        intent.putExtra("matchId",            getIntent().getStringExtra("matchId"));
        intent.putExtra("myId",               getIntent().getStringExtra("myId"));
        intent.putExtra("opponentId",         getIntent().getStringExtra("opponentId"));
        intent.putExtra("isPlayer1",          getIntent().getBooleanExtra("isPlayer1", true));
        intent.putExtra("totalMyScore",       prevMy + myScore);
        intent.putExtra("totalOpponentScore", prevOpp + opponentScore);
        startActivity(intent);
        finish();
    }
    private void confirmLeave() {
        new AlertDialog.Builder(this)
                .setTitle("Napusti igru?")
                .setMessage("Izgubićeš partiju. Nastavi?")
                .setPositiveButton("Da", (d, w) -> leaveGame())
                .setNegativeButton("Ne", null)
                .show();
    }

    private void leaveGame() {
        if (timer != null) timer.cancel();
        boolean isGuest = getIntent().getBooleanExtra("isGuest", false);
        Intent intent = isGuest
                ? new Intent(this, com.example.slagalica.ui.main.GuestActivity.class)
                : new Intent(this, com.example.slagalica.MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
    }
}