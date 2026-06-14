package com.example.slagalica.ui.games;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.data.model.SkockoCombo;
import com.example.slagalica.data.repository.GameRepository;
import com.example.slagalica.logic.SkockoLogic;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class SkockoActivity extends AppCompatActivity {

    // ── Symbols ───────────────────────────────────────────────────────────────
    private static final List<String> SYMBOLS = Arrays.asList("⬛", "⬜", "🔴", "💛", "🔺", "⭐");

    // ── UI ───────────────────────────────────────────────────────────────────
    private TextView tvTimer, tvMyScore, tvOpponentScore, tvRound;

    // Attempt rows [attempt 0-5][field 0-3]
    private TextView[][] tvAttemptFields;
    private TextView[]   tvAttemptMatches;

    // Opponent row
    private TextView[] tvOppFields;
    private TextView   tvOppMatches;

    // Symbol selector buttons (the emoji TextViews in llRoundSymbols)
    // We'll find them by index from the LinearLayout at runtime
    private TextView[] tvSymbolButtons;

    private MaterialButton btnFinish, btnLeave;

    // ── State ─────────────────────────────────────────────────────────────────
    private SkockoCombo combo;
    private int currentRound  = 1;
    private int myScore       = 0;
    private int opponentScore = 0;

    // Per-round
    private List<String> currentGuess;  // 4 slots being built
    private int          attemptIndex;  // 0-5
    private boolean      roundWon;
    private boolean      isMyTurn;

    private CountDownTimer timer;

    private final GameRepository repo = new GameRepository();

    // ────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skocko);

        bindViews();
        loadCombo();
    }

    // ── Bind ─────────────────────────────────────────────────────────────────
    private void bindViews() {
        tvTimer         = findViewById(R.id.tvTimer);
        tvMyScore       = findViewById(R.id.tvMyScore);
        tvOpponentScore = findViewById(R.id.tvOpponentScore);
        tvRound         = findViewById(R.id.tvRound);

        // Attempt fields
        int[][] attemptFieldIds = {
                { R.id.tvAttempt1Field1, R.id.tvAttempt1Field2, R.id.tvAttempt1Field3, R.id.tvAttempt1Field4 },
                { R.id.tvAttempt2Field1, R.id.tvAttempt2Field2, R.id.tvAttempt2Field3, R.id.tvAttempt2Field4 },
                { R.id.tvAttempt3Field1, R.id.tvAttempt3Field2, R.id.tvAttempt3Field3, R.id.tvAttempt3Field4 },
                { R.id.tvAttempt4Field1, R.id.tvAttempt4Field2, R.id.tvAttempt4Field3, R.id.tvAttempt4Field4 },
                { R.id.tvAttempt5Field1, R.id.tvAttempt5Field2, R.id.tvAttempt5Field3, R.id.tvAttempt5Field4 },
                { R.id.tvAttempt6Field1, R.id.tvAttempt6Field2, R.id.tvAttempt6Field3, R.id.tvAttempt6Field4 },
        };
        int[] matchIds = {
                R.id.tvAttempt1Matches, R.id.tvAttempt2Matches, R.id.tvAttempt3Matches,
                R.id.tvAttempt4Matches, R.id.tvAttempt5Matches, R.id.tvAttempt6Matches,
        };

        tvAttemptFields  = new TextView[6][4];
        tvAttemptMatches = new TextView[6];
        for (int i = 0; i < 6; i++) {
            tvAttemptMatches[i] = findViewById(matchIds[i]);
            for (int f = 0; f < 4; f++) {
                tvAttemptFields[i][f] = findViewById(attemptFieldIds[i][f]);
            }
        }

        tvOppFields = new TextView[]{
                findViewById(R.id.tvOppField1), findViewById(R.id.tvOppField2),
                findViewById(R.id.tvOppField3), findViewById(R.id.tvOppField4)
        };
        tvOppMatches = findViewById(R.id.tvOppMatches);

        // Symbol selector — the 6 emoji TextViews inside llRoundSymbols
        // IDs aren't set in XML so we access them via the LinearLayout's children
        android.widget.LinearLayout llRoundSymbols = findViewById(R.id.llRoundSymbols);
        // Children: tvRound (index 0), then 6 symbol TextViews
        tvSymbolButtons = new TextView[6];
        int symIdx = 0;
        for (int i = 0; i < llRoundSymbols.getChildCount() && symIdx < 6; i++) {
            android.view.View child = llRoundSymbols.getChildAt(i);
            if (child instanceof TextView && child.getId() != R.id.tvRound) {
                tvSymbolButtons[symIdx] = (TextView) child;
                final String sym = SYMBOLS.get(symIdx);
                tvSymbolButtons[symIdx].setOnClickListener(v -> onSymbolSelected(sym));
                symIdx++;
            }
        }

        btnFinish = findViewById(R.id.btnFinish);
        btnLeave  = findViewById(R.id.btnLeave);

        btnFinish.setOnClickListener(v -> goNext());
        btnLeave.setOnClickListener(v -> confirmLeave());

        // Submit button via guess row — we add a "Potvrdi" button click on btnFinish
        // during active play; it will submit the current guess row.
        // We reuse btnFinish: its label changes based on state.
        updateFinishButton(false);
    }

    // ── Load ──────────────────────────────────────────────────────────────────
    private void loadCombo() {
        repo.getRandomSkockoCombo(
                c -> { combo = c; startRound(1); },
                e -> Toast.makeText(this, "Greška pri učitavanju kombinacije.", Toast.LENGTH_SHORT).show()
        );
    }

    // ── Round lifecycle ───────────────────────────────────────────────────────
    private void startRound(int round) {
        currentRound = round;
        isMyTurn     = (round == 1);

        currentGuess = new ArrayList<>(Arrays.asList("", "", "", ""));
        attemptIndex = 0;
        roundWon     = false;

        tvRound.setText("Runda " + round + "/2");
        updateScoreBar();
        resetGrid();

        if (isMyTurn) {
            setSymbolsEnabled(true);
            updateFinishButton(false); // "Potvrdi" mode
            startTimer(30);
        } else {
            setSymbolsEnabled(false);
            simulateOpponentRound();
        }
    }

    private void resetGrid() {
        for (int i = 0; i < 6; i++) {
            tvAttemptMatches[i].setText("0/4 ✓");
            for (int f = 0; f < 4; f++) {
                tvAttemptFields[i][f].setText("?");
                tvAttemptFields[i][f].setBackgroundColor(getColor(R.color.primary_green_light));
            }
        }
        for (int f = 0; f < 4; f++) tvOppFields[f].setText("?");
        tvOppMatches.setText("-- ✓");
        clearCurrentGuessRow();
    }

    // ── Symbol selection & guess building ────────────────────────────────────
    private void onSymbolSelected(String symbol) {
        if (!isMyTurn || roundWon) return;
        // Fill next empty slot in current attempt row
        for (int f = 0; f < 4; f++) {
            if (currentGuess.get(f).isEmpty()) {
                currentGuess.set(f, symbol);
                tvAttemptFields[attemptIndex][f].setText(symbol);
                break;
            }
        }
        // If row full, show confirm option
        if (isRowFull()) {
            updateFinishButton(true); // "Potvrdi pokušaj"
        }
    }

    private boolean isRowFull() {
        for (String s : currentGuess) if (s.isEmpty()) return false;
        return true;
    }

    private void clearCurrentGuessRow() {
        currentGuess = new ArrayList<>(Arrays.asList("", "", "", ""));
        if (attemptIndex < 6) {
            for (int f = 0; f < 4; f++) {
                tvAttemptFields[attemptIndex][f].setText("?");
            }
        }
        updateFinishButton(false);
    }

    // ── Submit guess ──────────────────────────────────────────────────────────
    private void submitGuess() {
        if (!isRowFull()) {
            Toast.makeText(this, "Popuni sva 4 polja!", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> solution = combo.getCombination();
        boolean correct = SkockoLogic.isCorrect(currentGuess, solution);
        int matches     = SkockoLogic.countMatches(currentGuess, solution);

        // Lock current row display
        tvAttemptMatches[attemptIndex].setText(matches + "/4 ✓");

        if (correct) {
            roundWon = true;
            int pts = SkockoLogic.pointsForAttempt(attemptIndex + 1);
            myScore += pts;
            updateScoreBar();
            Toast.makeText(this, "Tačno! +" + pts + " bodova", Toast.LENGTH_SHORT).show();
            endMyTurn(true);
        } else {
            attemptIndex++;
            if (attemptIndex >= 6) {
                // All attempts used
                Toast.makeText(this, "Nisi pogodio kombinaciju.", Toast.LENGTH_SHORT).show();
                revealSolution();
                endMyTurn(false);
            } else {
                clearCurrentGuessRow();
            }
        }
    }

    // ── End of turn ───────────────────────────────────────────────────────────
    private void endMyTurn(boolean won) {
        if (timer != null) timer.cancel();
        setSymbolsEnabled(false);
        revealSolution();

        if (currentRound == 1) {
            // Move to round 2 — opponent's main turn
            tvRound.postDelayed(() -> startRound(2), 2000);
        } else {
            // Both rounds done
            showFinalScore();
        }
    }

    // ── Opponent simulation ───────────────────────────────────────────────────
    private void simulateOpponentRound() {
        tvTimer.setText("--");
        tvRound.postDelayed(() -> {
            // Opponent makes a random attempt; random chance to win
            List<String> oppGuess = randomGuess();
            List<String> solution = combo.getCombination();
            boolean oppCorrect = SkockoLogic.isCorrect(oppGuess, solution);
            int oppMatches     = SkockoLogic.countMatches(oppGuess, solution);

            for (int f = 0; f < 4; f++) tvOppFields[f].setText(oppGuess.get(f));
            tvOppMatches.setText(oppMatches + "/4 ✓");

            if (oppCorrect) {
                int pts = SkockoLogic.pointsForAttempt(1);
                opponentScore += pts;
                updateScoreBar();
                Toast.makeText(this, "Protivnik pogodio! +" + pts + " (njemu)", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Protivnik nije pogodio. Tvoj red!", Toast.LENGTH_SHORT).show();
            }

            // Give me a 10s chance (opponent-miss bonus opportunity)
            isMyTurn = true;
            setSymbolsEnabled(!oppCorrect); // Only if opp missed
            if (!oppCorrect) {
                startTimer(10); // 10-second opponent-miss bonus
                updateFinishButton(false);
            } else {
                // Opponent won; show result
                tvRound.postDelayed(this::showFinalScore, 1500);
            }
        }, 2000);
    }

    private List<String> randomGuess() {
        Random rnd = new Random();
        List<String> guess = new ArrayList<>();
        for (int i = 0; i < 4; i++) guess.add(SYMBOLS.get(rnd.nextInt(SYMBOLS.size())));
        return guess;
    }

    // ── Timer ─────────────────────────────────────────────────────────────────
    private void startTimer(int seconds) {
        tvTimer.setText(String.valueOf(seconds));
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(seconds * 1000L, 1000) {
            @Override public void onTick(long ms) { tvTimer.setText(String.valueOf((int)(ms / 1000))); }
            @Override public void onFinish() {
                tvTimer.setText("0");
                onTimeUp();
            }
        }.start();
    }

    private void onTimeUp() {
        setSymbolsEnabled(false);
        revealSolution();
        if (currentRound == 1) {
            tvRound.postDelayed(() -> startRound(2), 1500);
        } else {
            showFinalScore();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private void revealSolution() {
        List<String> sol = combo.getCombination();
        // Show solution in last opp row if not already revealed
        for (int f = 0; f < 4; f++) tvOppFields[f].setText(sol.get(f));
        tvOppMatches.setText("✅");
    }

    private void setSymbolsEnabled(boolean enabled) {
        if (tvSymbolButtons == null) return;
        for (TextView tv : tvSymbolButtons) {
            if (tv != null) tv.setEnabled(enabled);
        }
    }

    private void updateScoreBar() {
        tvMyScore.setText(String.valueOf(myScore));
        tvOpponentScore.setText(String.valueOf(opponentScore));
    }

    /**
     * Reuse btnFinish:
     *   isConfirmMode=true  → "Potvrdi pokušaj" (submits guess)
     *   isConfirmMode=false → "Sledeća igra →"  (goes next)
     */
    private void updateFinishButton(boolean isConfirmMode) {
        if (isConfirmMode) {
            btnFinish.setText("Potvrdi pokušaj");
            btnFinish.setOnClickListener(v -> submitGuess());
        } else {
            btnFinish.setText("Sledeća igra →");
            btnFinish.setOnClickListener(v -> goNext());
        }
    }

    private void showFinalScore() {
        revealSolution();
        setSymbolsEnabled(false);
        if (timer != null) timer.cancel();

        new AlertDialog.Builder(this)
                .setTitle("Skočko završen")
                .setMessage("Tvoji bodovi: " + myScore + "\nProtivnik: " + opponentScore)
                .setCancelable(false)
                .setPositiveButton("Dalje", (d, w) -> goNext())
                .show();
    }

    private void goNext() {
        if (timer != null) timer.cancel();
        Intent intent = new Intent(this, MojBrojActivity.class);
        intent.putExtra("isGuest", getIntent().getBooleanExtra("isGuest", false));
        intent.putExtra("scoreSkocko", myScore);
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