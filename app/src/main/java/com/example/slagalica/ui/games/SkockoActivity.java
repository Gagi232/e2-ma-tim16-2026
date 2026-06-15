package com.example.slagalica.ui.games;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.widget.LinearLayout;
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

    private static final List<String> SYMBOLS = Arrays.asList("⬛", "⬜", "🔴", "💛", "🔺", "⭐");

    // ── UI ───────────────────────────────────────────────────────────────────
    private TextView tvTimer, tvMyScore, tvOpponentScore, tvRound;
    private TextView[][] tvAttemptFields;   // [6][4] — runda 1: moji; runda 2: protivnikovi
    private LinearLayout[] llAttemptFeedback;
    private TextView[] tvOppFields;         // 7. red — moj pokušaj u rundi 2
    private LinearLayout llOppFeedback;
    private TextView[] tvSymbolButtons;
    private MaterialButton btnFinish, btnLeave, btnDelete;

    // ── State ─────────────────────────────────────────────────────────────────
    private SkockoCombo combo;
    private int currentRound  = 1;
    private int myScore       = 0;
    private int opponentScore = 0;
    private List<String> currentGuess;
    private int attemptIndex;
    private boolean roundWon;
    private boolean isMyTurn;
    // Runda 2: da li je protivnik vec odigrao (simulirano) i cekamo moj pokusaj
    private boolean waitingForMyR2Guess = false;

    private CountDownTimer timer;
    private final GameRepository repo = new GameRepository();

    // ────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skocko);
        bindViews();
        loadComboAndStart(1);
    }

    private void bindViews() {
        tvTimer         = findViewById(R.id.tvTimer);
        tvMyScore       = findViewById(R.id.tvMyScore);
        tvOpponentScore = findViewById(R.id.tvOpponentScore);
        tvRound         = findViewById(R.id.tvRound);
        btnFinish       = findViewById(R.id.btnFinish);
        btnLeave        = findViewById(R.id.btnLeave);
        btnDelete       = findViewById(R.id.btnDelete);

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

        // Symbol buttons
        android.widget.LinearLayout llRoundSymbols = findViewById(R.id.llRoundSymbols);
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

        btnDelete.setOnClickListener(v -> deleteLastSymbol());
        btnLeave.setOnClickListener(v -> confirmLeave());
        updateFinishButton(false);
    }

    // ── Load & start ──────────────────────────────────────────────────────────
    private void loadComboAndStart(int round) {
        repo.getRandomSkockoCombo(
                c -> { combo = c; startRound(round); },
                e -> Toast.makeText(this, "Greška pri učitavanju kombinacije.", Toast.LENGTH_SHORT).show()
        );
    }

    private void startRound(int round) {
        currentRound        = round;
        isMyTurn            = true;
        waitingForMyR2Guess = false;
        currentGuess        = new ArrayList<>(Arrays.asList("", "", "", ""));
        attemptIndex        = 0;
        roundWon            = false;

        tvRound.setText("Runda " + round + "/2");
        updateScoreBar();
        resetGrid();

        if (round == 1) {
            // Runda 1: ja igram normalno, 6 pokusaja, 60 sekundi
            setSymbolsEnabled(true);
            btnDelete.setEnabled(true);
            updateFinishButton(false);
            startTimer(60);
        } else {
            // Runda 2: prvo simuliraj protivnika na prvih 6 redova,
            // pa onda daj meni jedan pokusaj u 7. redu
            setSymbolsEnabled(false);
            btnDelete.setEnabled(false);
            updateFinishButton(false);
            tvTimer.setText("--");
            simulateOpponentThenMyTurn();
        }
    }

    private void resetGrid() {
        for (int i = 0; i < 6; i++) {
            for (int f = 0; f < 4; f++) {
                tvAttemptFields[i][f].setText("?");
                tvAttemptFields[i][f].setBackgroundColor(getColor(R.color.primary_green_light));
            }
            setFeedbackCircles(llAttemptFeedback[i], -1, -1);
        }
        for (int f = 0; f < 4; f++) {
            tvOppFields[f].setText("?");
            tvOppFields[f].setBackgroundColor(0xFFF0F0F0);
        }
        setFeedbackCircles(llOppFeedback, -1, -1);
    }

    // ── Runda 2: simulacija protivnika pa moj pokušaj ─────────────────────────
    private void simulateOpponentThenMyTurn() {
        List<String> solution = combo.getCombination();
        Random rnd = new Random();

        // Simuliraj protivnikovih 6 pokusaja sa delay-om
        // Koristimo rekurzivni postDelayed da animiramo red po red
        simulateOppRow(0, solution, rnd);
    }

    private void simulateOppRow(int row, List<String> solution, Random rnd) {
        if (row >= 6) {
            // Svih 6 protivnikovih pokusaja prikazano — daj mi moj red
            tvRound.postDelayed(() -> giveMyR2Turn(solution), 800);
            return;
        }

        tvRound.postDelayed(() -> {
            List<String> oppGuess = randomGuess(rnd);
            int matches  = SkockoLogic.countMatches(oppGuess, solution);
            int wrongPos = SkockoLogic.countWrongPosition(oppGuess, solution);
            boolean correct = SkockoLogic.isCorrect(oppGuess, solution);

            for (int f = 0; f < 4; f++)
                tvAttemptFields[row][f].setText(oppGuess.get(f));
            setFeedbackCircles(llAttemptFeedback[row], matches, wrongPos);

            if (correct) {
                // Protivnik pogodio pre 6. reda — daj bodove i preskoci ostatak
                int pts = SkockoLogic.pointsForAttempt(row + 1);
                opponentScore += pts;
                updateScoreBar();
                Toast.makeText(this, "Protivnik pogodio u " + (row+1) + ". pokušaju!", Toast.LENGTH_SHORT).show();
                // Popuni ostatak redova prazno i idi na moj red
                tvRound.postDelayed(() -> giveMyR2Turn(solution), 800);
                return;
            }

            // Sledeci red
            simulateOppRow(row + 1, solution, rnd);
        }, 400L * (row + 1));
    }

    private void giveMyR2Turn(List<String> solution) {
        waitingForMyR2Guess = true;
        isMyTurn = true;
        currentGuess = new ArrayList<>(Arrays.asList("", "", "", ""));

        // Ocisti 7. red (opp red) za moj unos
        for (int f = 0; f < 4; f++) {
            tvOppFields[f].setText("?");
            tvOppFields[f].setBackgroundColor(getColor(R.color.primary_green_light));
        }
        setFeedbackCircles(llOppFeedback, -1, -1);

        Toast.makeText(this, "Tvoj red! Pogodi protivnikovu kombinaciju.", Toast.LENGTH_SHORT).show();
        setSymbolsEnabled(true);
        btnDelete.setEnabled(true);
        updateFinishButton(false);
        startTimer(30);
    }

    // ── Symbol selection ──────────────────────────────────────────────────────
    private void onSymbolSelected(String symbol) {
        if (!isMyTurn || roundWon) return;

        if (waitingForMyR2Guess) {
            // Popunjavam 7. red (tvOppFields)
            for (int f = 0; f < 4; f++) {
                if (currentGuess.get(f).isEmpty()) {
                    currentGuess.set(f, symbol);
                    tvOppFields[f].setText(symbol);
                    break;
                }
            }
            if (isRowFull()) updateFinishButton(true);
        } else {
            // Runda 1 — popunjavam attempt redove
            for (int f = 0; f < 4; f++) {
                if (currentGuess.get(f).isEmpty()) {
                    currentGuess.set(f, symbol);
                    tvAttemptFields[attemptIndex][f].setText(symbol);
                    break;
                }
            }
            if (isRowFull()) updateFinishButton(true);
        }
    }

    private void deleteLastSymbol() {
        if (!isMyTurn || roundWon) return;

        if (waitingForMyR2Guess) {
            for (int f = 3; f >= 0; f--) {
                if (!currentGuess.get(f).isEmpty()) {
                    currentGuess.set(f, "");
                    tvOppFields[f].setText("?");
                    updateFinishButton(false);
                    break;
                }
            }
        } else {
            for (int f = 3; f >= 0; f--) {
                if (!currentGuess.get(f).isEmpty()) {
                    currentGuess.set(f, "");
                    tvAttemptFields[attemptIndex][f].setText("?");
                    updateFinishButton(false);
                    break;
                }
            }
        }
    }

    private boolean isRowFull() {
        for (String s : currentGuess) if (s.isEmpty()) return false;
        return true;
    }

    private void clearCurrentGuessRow() {
        currentGuess = new ArrayList<>(Arrays.asList("", "", "", ""));
        if (attemptIndex < 6) {
            for (int f = 0; f < 4; f++) tvAttemptFields[attemptIndex][f].setText("?");
        }
        updateFinishButton(false);
    }

    // ── Submit ────────────────────────────────────────────────────────────────
    private void submitGuess() {
        if (!isRowFull()) {
            Toast.makeText(this, "Popuni sva 4 polja!", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> solution = combo.getCombination();

        if (waitingForMyR2Guess) {
            // Moj jedini pokusaj u rundi 2
            boolean correct  = SkockoLogic.isCorrect(currentGuess, solution);
            int matches      = SkockoLogic.countMatches(currentGuess, solution);
            int wrongPos     = SkockoLogic.countWrongPosition(currentGuess, solution);

            setFeedbackCircles(llOppFeedback, matches, wrongPos);

            if (correct) {
                int pts = SkockoLogic.pointsForAttempt(1); // bonus pokusaj
                myScore += pts;
                updateScoreBar();
                Toast.makeText(this, "Pogodio! +" + pts + " bodova", Toast.LENGTH_SHORT).show();
            } else {
                // Otkrij tacnu kombinaciju
                for (int f = 0; f < 4; f++) tvOppFields[f].setText(solution.get(f));
                setFeedbackCircles(llOppFeedback, 4, 0);
                Toast.makeText(this, "Nisi pogodio.", Toast.LENGTH_SHORT).show();
            }

            if (timer != null) timer.cancel();
            setSymbolsEnabled(false);
            btnDelete.setEnabled(false);
            waitingForMyR2Guess = false;
            tvRound.postDelayed(this::showFinalScore, 1500);

        } else {
            // Runda 1 — normalan tok
            boolean correct = SkockoLogic.isCorrect(currentGuess, solution);
            int matches     = SkockoLogic.countMatches(currentGuess, solution);
            int wrongPos    = SkockoLogic.countWrongPosition(currentGuess, solution);

            setFeedbackCircles(llAttemptFeedback[attemptIndex], matches, wrongPos);

            if (correct) {
                roundWon = true;
                int pts = SkockoLogic.pointsForAttempt(attemptIndex + 1);
                myScore += pts;
                updateScoreBar();
                Toast.makeText(this, "Tačno! +" + pts + " bodova", Toast.LENGTH_SHORT).show();
                endRound1();
            } else {
                attemptIndex++;
                if (attemptIndex >= 6) {
                    Toast.makeText(this, "Nisi pogodio kombinaciju.", Toast.LENGTH_SHORT).show();
                    endRound1();
                } else {
                    clearCurrentGuessRow();
                }
            }
        }
    }

    private void endRound1() {
        if (timer != null) timer.cancel();
        setSymbolsEnabled(false);
        btnDelete.setEnabled(false);
        updateFinishButton(false);

        // Otkrij resenje u opp redu
        List<String> sol = combo.getCombination();
        for (int f = 0; f < 4; f++) tvOppFields[f].setText(sol.get(f));
        setFeedbackCircles(llOppFeedback, 4, 0);

        // Ucitaj novu kombinaciju za rundu 2
        tvRound.postDelayed(() -> loadComboAndStart(2), 2000);
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
        btnDelete.setEnabled(false);

        if (waitingForMyR2Guess) {
            // Isteklo vreme za moj pokusaj u rundi 2
            List<String> sol = combo.getCombination();
            for (int f = 0; f < 4; f++) tvOppFields[f].setText(sol.get(f));
            setFeedbackCircles(llOppFeedback, 4, 0);
            waitingForMyR2Guess = false;
            tvRound.postDelayed(this::showFinalScore, 1500);
        } else {
            // Isteklo vreme u rundi 1
            endRound1();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private List<String> randomGuess(Random rnd) {
        List<String> guess = new ArrayList<>();
        for (int i = 0; i < 4; i++) guess.add(SYMBOLS.get(rnd.nextInt(SYMBOLS.size())));
        return guess;
    }

    private void setFeedbackCircles(LinearLayout ll, int correct, int wrongPos) {
        ll.removeAllViews();
        ll.setOrientation(LinearLayout.HORIZONTAL);
        ll.setGravity(Gravity.CENTER);

        int red    = correct < 0 ? 0 : correct;
        int yellow = correct < 0 ? 0 : wrongPos;
        int empty  = correct < 0 ? 4 : Math.max(0, 4 - red - yellow);

        for (int i = 0; i < red; i++)    ll.addView(makeCircle("#E53935"));
        for (int i = 0; i < yellow; i++) ll.addView(makeCircle("#FDD835"));
        for (int i = 0; i < empty; i++)  ll.addView(makeCircle("#CCCCCC"));
    }

    private android.view.View makeCircle(String hexColor) {
        android.view.View v = new android.view.View(this);
        int size = dpToPx(12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(dpToPx(2), 0, dpToPx(2), 0);
        v.setLayoutParams(lp);
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        shape.setColor(android.graphics.Color.parseColor(hexColor));
        v.setBackground(shape);
        return v;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void setSymbolsEnabled(boolean enabled) {
        if (tvSymbolButtons == null) return;
        for (TextView tv : tvSymbolButtons) if (tv != null) tv.setEnabled(enabled);
    }

    private void updateScoreBar() {
        tvMyScore.setText(String.valueOf(myScore));
        tvOpponentScore.setText(String.valueOf(opponentScore));
    }

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
        if (timer != null) timer.cancel();
        setSymbolsEnabled(false);
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