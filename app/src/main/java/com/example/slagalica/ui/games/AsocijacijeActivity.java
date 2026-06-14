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

import java.util.ArrayList;
import java.util.List;

public class AsocijacijeActivity extends AppCompatActivity {

    // ── UI ──────────────────────────────────────────────────────────────────
    private TextView tvTimer, tvMyScore, tvOpponentScore, tvRound, tvFinalSolution;

    // Col fields [col 0-3][field 0-3]
    private TextView[][] tvFields;
    // Col solution labels
    private TextView[] tvColSolution;
    // CardViews for col fields (for click handling)
    private CardView[][] cvFields;
    // CardViews for col solutions
    private CardView[] cvColSolution;

    private CardView cardFinalSolution;
    private TextInputEditText etAnswer;
    private MaterialButton btnFinish, btnLeave;

    // ── State ────────────────────────────────────────────────────────────────
    private Association association;
    private int currentRound = 1;   // 1 or 2
    private int myScore = 0;
    private int opponentScore = 0;

    // Per-round state (reset between rounds)
    private boolean[] colSolved;          // col 0-3: has THIS player solved it
    private int[]     hiddenFields;       // col 0-3: how many fields still hidden
    private boolean[] fieldRevealed;      // flat [col*4 + field]
    private boolean   finalSolved;
    private boolean   isMyTurn;           // true = current round is mine

    private CountDownTimer timer;
    private int timeLeft;

    // Which player starts round 1: me
    // (Round 1 → me, Round 2 → opponent simulated)
    private static final int ROUND_SECS = 120;

    // ── Repo ─────────────────────────────────────────────────────────────────
    private final GameRepository repo = new GameRepository();

    // ────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_asocijacije);

        bindViews();
        loadAssociation();
    }

    // ── Bind ─────────────────────────────────────────────────────────────────
    private void bindViews() {
        tvTimer          = findViewById(R.id.tvTimer);
        tvMyScore        = findViewById(R.id.tvMyScore);
        tvOpponentScore  = findViewById(R.id.tvOpponentScore);
        tvRound          = findViewById(R.id.tvRound);
        tvFinalSolution  = findViewById(R.id.tvFinalSolution);
        cardFinalSolution = findViewById(R.id.cardFinalSolution);
        etAnswer         = findViewById(R.id.etAnswer);
        btnFinish        = findViewById(R.id.btnFinish);
        btnLeave         = findViewById(R.id.btnLeave);

        // Fields: tvCol1Field1 … tvCol4Field4
        int[][] fieldIds = {
                { R.id.tvCol1Field1, R.id.tvCol1Field2, R.id.tvCol1Field3, R.id.tvCol1Field4 },
                { R.id.tvCol2Field1, R.id.tvCol2Field2, R.id.tvCol2Field3, R.id.tvCol2Field4 },
                { R.id.tvCol3Field1, R.id.tvCol3Field2, R.id.tvCol3Field3, R.id.tvCol3Field4 },
                { R.id.tvCol4Field1, R.id.tvCol4Field2, R.id.tvCol4Field3, R.id.tvCol4Field4 },
        };
        // CardView parents — we tag each CardView with (col, field) via setTag
        // Since the XML wraps each TextView in a CardView we traverse parent in click.
        tvFields = new TextView[4][4];
        cvFields = new CardView[4][4]; // not directly in XML ids, resolved via parent
        for (int c = 0; c < 4; c++) {
            for (int f = 0; f < 4; f++) {
                tvFields[c][f] = findViewById(fieldIds[c][f]);
            }
        }

        int[] solIds = {
                R.id.tvCol1Solution, R.id.tvCol2Solution,
                R.id.tvCol3Solution, R.id.tvCol4Solution
        };
        tvColSolution = new TextView[4];
        for (int c = 0; c < 4; c++) tvColSolution[c] = findViewById(solIds[c]);

        // Button listeners
        btnFinish.setOnClickListener(v -> goNext());
        btnLeave.setOnClickListener(v -> confirmLeave());
    }

    // ── Load data ────────────────────────────────────────────────────────────
    private void loadAssociation() {
        repo.getRandomAssociation(
                assoc -> {
                    association = assoc;
                    startRound(1);
                },
                e -> Toast.makeText(this, "Greška pri učitavanju asocijacije.", Toast.LENGTH_SHORT).show()
        );
    }

    // ── Round lifecycle ───────────────────────────────────────────────────────
    private void startRound(int round) {
        currentRound = round;
        isMyTurn = (round == 1);  // Round 1: I go first; Round 2: opponent goes first (simulated)

        // Reset per-round state
        colSolved     = new boolean[4];
        hiddenFields  = new int[]{4, 4, 4, 4};
        fieldRevealed = new boolean[16];
        finalSolved   = false;

        tvRound.setText("Runda " + round + "/2");
        updateScoreBar();
        resetGrid();

        if (isMyTurn) {
            enableInput(true);
            startTimer(ROUND_SECS);
        } else {
            // Opponent's turn — simulate and then give me a chance
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
            tvColSolution[c].setText("?");
        }
        cardFinalSolution.setVisibility(View.VISIBLE);
        tvFinalSolution.setText("KONAČNO REŠENJE");
        etAnswer.setText("");
        etAnswer.setHint("Unesite odgovor...");
    }

    // ── Field click ───────────────────────────────────────────────────────────
    private void onFieldClick(int col, int field) {
        if (!isMyTurn) return;
        int idx = col * 4 + field;
        if (fieldRevealed[idx]) return;

        // Reveal field
        fieldRevealed[idx] = true;
        hiddenFields[col]--;
        String word = getWord(col, field);
        tvFields[col][field].setText(word);
        tvFields[col][field].setBackgroundColor(getColor(R.color.primary_green_dark));

        // Prompt guess
        showGuessDialog(col);
    }

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

    // ── Guess dialog ──────────────────────────────────────────────────────────
    private void showGuessDialog(int openedCol) {
        String[] options = buildGuessOptions();
        new AlertDialog.Builder(this)
                .setTitle("Šta pogađaš?")
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        // Guess column solution
                        showColGuessInput(openedCol);
                    } else {
                        // Guess final solution
                        showFinalGuessInput();
                    }
                })
                .show();
    }

    private String[] buildGuessOptions() {
        List<String> opts = new ArrayList<>();
        opts.add("Rešenje kolone");
        opts.add("Konačno rešenje");
        return opts.toArray(new String[0]);
    }

    private void showColGuessInput(int col) {
        if (colSolved[col]) {
            Toast.makeText(this, "Ta kolona je već pogođena.", Toast.LENGTH_SHORT).show();
            return;
        }
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Rešenje kolone " + (col + 1));
        new AlertDialog.Builder(this)
                .setTitle("Pogodi rešenje kolone " + (col + 1))
                .setView(input)
                .setPositiveButton("Potvrdi", (d, w) -> {
                    String answer = input.getText().toString().trim();
                    checkColGuess(col, answer);
                })
                .setNegativeButton("Odustani", null)
                .show();
    }

    private void checkColGuess(int col, String answer) {
        String correct = getColSolution(col);
        if (answer.equalsIgnoreCase(correct)) {
            colSolved[col] = true;
            tvColSolution[col].setText(correct);
            int pts = AssociationLogic.colScore(hiddenFields[col]);
            myScore += pts;
            updateScoreBar();
            Toast.makeText(this, "Tačno! +" + pts + " bodova", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Netačno.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showFinalGuessInput() {
        if (finalSolved) return;
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Konačno rešenje");
        new AlertDialog.Builder(this)
                .setTitle("Pogodi konačno rešenje")
                .setView(input)
                .setPositiveButton("Potvrdi", (d, w) -> {
                    String answer = input.getText().toString().trim();
                    checkFinalGuess(answer);
                })
                .setNegativeButton("Odustani", null)
                .show();
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
            // My turn ended — reveal all, move to round 2 or end
            revealAll();
            endRoundForPlayer();
        }
    }

    private void endRoundForPlayer() {
        if (timer != null) timer.cancel();
        enableInput(false);
        revealAll();

        if (currentRound == 1) {
            // Short delay then start round 2 (opponent's round)
            tvRound.postDelayed(() -> startRound(2), 1500);
        } else {
            // Both rounds done
            showFinalScore();
        }
    }

    // ── Opponent simulation ───────────────────────────────────────────────────
    /**
     * Simple simulation: opponent "plays" round 2 and scores some points.
     * After short delay, we reveal opponent result and give player their turn.
     */
    private void simulateOpponentRound() {
        // Opponent randomly solves 1-2 columns
        int oppPts = 0;
        java.util.Random rnd = new java.util.Random();
        for (int c = 0; c < 4; c++) {
            if (rnd.nextBoolean()) {
                int hidden = rnd.nextInt(4); // opponent opened some fields
                oppPts += AssociationLogic.colScore(hidden);
                tvColSolution[c].setText(getColSolution(c));
                colSolved[c] = true;
                hiddenFields[c] = hidden;
            }
        }
        final int finalOppPts = oppPts;
        tvTimer.setText("--");

        tvRound.postDelayed(() -> {
            opponentScore += finalOppPts;
            updateScoreBar();
            Toast.makeText(this, "Protivnik odigrao. Sada ti ideš!", Toast.LENGTH_SHORT).show();

            // Player gets their sub-turn in round 2
            isMyTurn = true;
            enableInput(true);
            startTimer(ROUND_SECS);
        }, 2000);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
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
        Intent intent = new Intent(this, SkockoActivity.class);
        intent.putExtra("isGuest", getIntent().getBooleanExtra("isGuest", false));
        intent.putExtra("scoreAsocijacije", myScore);
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
        intent.putExtra("isGuest", isGuest);
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