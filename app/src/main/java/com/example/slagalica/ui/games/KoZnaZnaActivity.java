package com.example.slagalica.ui.games;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.MainActivity;
import com.example.slagalica.R;
import com.example.slagalica.data.model.KoZnaZnaQuestion;
import com.example.slagalica.data.repository.GameRepository;
import com.example.slagalica.data.repository.StatsRepository;
import com.example.slagalica.logic.KoZnaZnaLogic;
import com.example.slagalica.ui.main.GuestActivity;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KoZnaZnaActivity extends AppCompatActivity {

    // UI - ID-evi iz activity_ko_zna_zna.xml
    private TextView tvQuestion;
    private TextView tvTimer;
    private TextView tvMyScore;
    private TextView tvOpponentScore;
    private TextView tvQuestionNumber;
    private MaterialButton btnAnswer1;
    private MaterialButton btnAnswer2;
    private MaterialButton btnAnswer3;
    private MaterialButton btnAnswer4;

    // Logika
    private final KoZnaZnaLogic logic = new KoZnaZnaLogic();

    // Podaci
    private final GameRepository gameRepo = new GameRepository();
    private List<KoZnaZnaQuestion> questions = new ArrayList<>();

    // Stanje
    private int currentIndex = 0;
    private int myScore = 0;
    private int opponentScore = 0;
    private boolean answered = false;
    private CountDownTimer timer;

    // Match
    private String matchId;
    private String myId;
    private String opponentId;
    private boolean isPlayer1;
    private boolean isGuest;


    private int correctCount = 0;
    private int wrongCount   = 0;
    // Firebase Realtime DB
    private DatabaseReference matchRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ko_zna_zna);

        isGuest    = getIntent().getBooleanExtra("isGuest", false);
        matchId    = getIntent().getStringExtra("matchId");
        myId       = getIntent().getStringExtra("myId");
        opponentId = getIntent().getStringExtra("opponentId");
        isPlayer1  = getIntent().getBooleanExtra("isPlayer1", true);

        bindViews();

        // btnFinish sakrivamo - igra sama napreduje
        findViewById(R.id.btnFinish).setVisibility(View.GONE);
        findViewById(R.id.btnLeave).setOnClickListener(v -> forfeit());

        if (isGuest || matchId == null) {
            loadSolo();
        } else {
            matchRef = FirebaseDatabase.getInstance()
                    .getReference("activeMatches")
                    .child(matchId)
                    .child("kzz");
            setupMultiplayer();
        }
    }

    private void bindViews() {
        tvQuestion       = findViewById(R.id.tvQuestion);
        tvTimer          = findViewById(R.id.tvTimer);
        tvMyScore        = findViewById(R.id.tvMyScore);
        tvOpponentScore  = findViewById(R.id.tvOpponentScore);
        tvQuestionNumber = findViewById(R.id.tvQuestionNumber);
        btnAnswer1       = findViewById(R.id.btnAnswer1);
        btnAnswer2       = findViewById(R.id.btnAnswer2);
        btnAnswer3       = findViewById(R.id.btnAnswer3);
        btnAnswer4       = findViewById(R.id.btnAnswer4);

        btnAnswer1.setOnClickListener(v -> onAnswered(0));
        btnAnswer2.setOnClickListener(v -> onAnswered(1));
        btnAnswer3.setOnClickListener(v -> onAnswered(2));
        btnAnswer4.setOnClickListener(v -> onAnswered(3));
    }

    // ── Ucitavanje pitanja ────────────────────────────────────────────────────

    private void loadSolo() {
        gameRepo.getRandomKoZnaZnaQuestions(
                KoZnaZnaLogic.TOTAL_QUESTIONS,
                result -> {
                    questions = result;
                    showQuestion(0);
                },
                error -> showError()
        );
    }

    private void setupMultiplayer() {
        if (isPlayer1) {
            gameRepo.getRandomKoZnaZnaQuestions(
                    KoZnaZnaLogic.TOTAL_QUESTIONS,
                    result -> {
                        questions = result;
                        List<String> ids = new ArrayList<>();
                        for (KoZnaZnaQuestion q : result) ids.add(q.getId());
                        matchRef.child("questionIds").setValue(ids)
                                .addOnSuccessListener(v -> showQuestion(0));
                    },
                    error -> showError()
            );
        } else {
            waitForQuestionIds();
        }
    }

    private void waitForQuestionIds() {
        matchRef.child("questionIds").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<String> ids = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    ids.add(child.getValue(String.class));
                }
                if (ids.isEmpty()) {
                    new Handler().postDelayed(() -> waitForQuestionIds(), 800);
                    return;
                }
                gameRepo.getQuestionsByIds(
                        ids,
                        result -> {
                            questions = result;
                            showQuestion(0);
                        },
                        error -> showError()
                );
            }

            @Override
            public void onCancelled(DatabaseError error) {
                showError();
            }
        });
    }

    // ── Prikaz pitanja ────────────────────────────────────────────────────────

    private void showQuestion(int index) {
        if (index >= questions.size()) {
            endGame();
            return;
        }

        currentIndex = index;
        answered = false;

        KoZnaZnaQuestion q = questions.get(index);
        tvQuestion.setText(q.getQuestion());
        tvQuestionNumber.setText("Pitanje " + (index + 1) + "/" + KoZnaZnaLogic.TOTAL_QUESTIONS);

        List<String> opts = q.getOptions();
        btnAnswer1.setText(opts.get(0));
        btnAnswer2.setText(opts.get(1));
        btnAnswer3.setText(opts.get(2));
        btnAnswer4.setText(opts.get(3));

        resetColors();
        enableButtons(true);
        updateScores();
        startTimer();

        if (matchRef != null) {
            listenForOpponentAnswer(index);
        }
    }

    // ── Timer ─────────────────────────────────────────────────────────────────

    private void startTimer() {
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(KoZnaZnaLogic.QUESTION_TIME_MS, 100) {
            @Override
            public void onTick(long millisLeft) {
                tvTimer.setText(String.valueOf((millisLeft / 1000) + 1));
            }

            @Override
            public void onFinish() {
                tvTimer.setText("0");
                if (!answered) onTimeout();
            }
        }.start();
    }

    // ── Odgovor ───────────────────────────────────────────────────────────────

    private void onAnswered(int selectedIndex) {
        if (answered) return;
        answered = true;
        if (timer != null) timer.cancel();

        long timestamp = System.currentTimeMillis();
        boolean correct = (selectedIndex == questions.get(currentIndex).getCorrectIndex());
        if (correct) correctCount++;
        else if (selectedIndex >= 0) wrongCount++; // -1 je timeout, ne broji se
        showFeedback(selectedIndex, questions.get(currentIndex).getCorrectIndex());

        if (matchRef != null) {
            writeAnswer(selectedIndex, correct, timestamp);
        } else {
            // Solo bodovanje kroz logiku
            myScore += logic.calcSoloScore(selectedIndex,
                    questions.get(currentIndex).getCorrectIndex());
            updateScores();
        }

        new Handler().postDelayed(() -> showQuestion(currentIndex + 1), 1500);
    }

    private void onTimeout() {
        answered = true;
        if (matchRef != null) {
            writeAnswer(-1, false, -1L);
        }
        new Handler().postDelayed(() -> showQuestion(currentIndex + 1), 500);
    }

    private void writeAnswer(int index, boolean correct, long timestamp) {
        Map<String, Object> data = new HashMap<>();
        data.put("answerIndex", index);
        data.put("correct", correct);
        data.put("timestamp", timestamp);
        matchRef.child("answers")
                .child(myId)
                .child(String.valueOf(currentIndex))
                .setValue(data);
    }

    // ── Multiplayer bodovanje ─────────────────────────────────────────────────

    private void listenForOpponentAnswer(int qIndex) {
        matchRef.child("answers").child(opponentId).child(String.valueOf(qIndex))
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot oppSnapshot) {
                        if (!oppSnapshot.exists()) return;
                        matchRef.child("answers").child(myId).child(String.valueOf(qIndex))
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(DataSnapshot mySnapshot) {
                                        if (!mySnapshot.exists()) return;
                                        applyMultiplayerScore(mySnapshot, oppSnapshot);
                                    }
                                    @Override
                                    public void onCancelled(DatabaseError error) {}
                                });
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {}
                });
    }

    private void applyMultiplayerScore(DataSnapshot mySnap, DataSnapshot oppSnap) {
        Boolean myCorrect  = mySnap.child("correct").getValue(Boolean.class);
        Boolean oppCorrect = oppSnap.child("correct").getValue(Boolean.class);
        Long    myTs       = mySnap.child("timestamp").getValue(Long.class);
        Long    oppTs      = oppSnap.child("timestamp").getValue(Long.class);
        Integer myIdx      = mySnap.child("answerIndex").getValue(Integer.class);
        Integer oppIdx     = oppSnap.child("answerIndex").getValue(Integer.class);

        if (myCorrect  == null) myCorrect  = false;
        if (oppCorrect == null) oppCorrect = false;
        if (myTs       == null) myTs       = Long.MAX_VALUE;
        if (oppTs      == null) oppTs      = Long.MAX_VALUE;
        if (myIdx      == null) myIdx      = -1;
        if (oppIdx     == null) oppIdx     = -1;

        // Logika racuna bodove
        int[] pts = logic.calcMultiScore(myIdx, myCorrect, myTs, oppIdx, oppCorrect, oppTs);
        myScore      += pts[0];
        opponentScore += pts[1];

        runOnUiThread(this::updateScores);
    }

    // ── Kraj igre ─────────────────────────────────────────────────────────────

    private void endGame() {
        if (timer != null) timer.cancel();

        if (matchRef != null) {
            matchRef.child("finalScore").child(myId).setValue(myScore);
            matchRef.child("done").child(myId).setValue(true);
        }

        // ── NOVO: sačuvaj statistiku Ko zna zna ──
        int correct = 0, wrong = 0;
        // Rekonstruiši correct/wrong iz bodova (svaki tačan = +10, netačan = -5)
        // Alternativno: dodaj brojače u klasu
        StatsRepository statsRepo = new StatsRepository();
        statsRepo.saveKoZnaZnaResult(correctCount, wrongCount, myScore,
                new StatsRepository.Callback<Void>() {
                    @Override public void onSuccess(Void r) {}
                    @Override public void onError(Exception e) {}
                });

        Intent intent = new Intent(this, SpojniceActivity.class);
        intent.putExtra("isGuest",          isGuest);
        intent.putExtra("matchId",          matchId);
        intent.putExtra("myId",             myId);
        intent.putExtra("opponentId",       opponentId);
        intent.putExtra("isPlayer1",        isPlayer1);
        intent.putExtra("kzzMyScore",       myScore);
        intent.putExtra("kzzOpponentScore", opponentScore);
        startActivity(intent);
        finish();
    }
    private void forfeit() {
        if (timer != null) timer.cancel();
        if (matchRef != null) {
            matchRef.getParent().child("status").setValue("forfeit_" + myId);
        }
        Intent i = new Intent(this, isGuest ? GuestActivity.class : MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
        finish();
    }

    // ── UI pomocne metode ─────────────────────────────────────────────────────

    private void showFeedback(int selected, int correct) {
        enableButtons(false);
        getButton(correct).setBackgroundTintList(ColorStateList.valueOf(0xFF4CAF50));
        if (selected != correct) {
            getButton(selected).setBackgroundTintList(ColorStateList.valueOf(0xFFF44336));
        }
    }

    private void resetColors() {
        int green = 0xFF388E3C;
        btnAnswer1.setBackgroundTintList(ColorStateList.valueOf(green));
        btnAnswer2.setBackgroundTintList(ColorStateList.valueOf(green));
        btnAnswer3.setBackgroundTintList(ColorStateList.valueOf(green));
        btnAnswer4.setBackgroundTintList(ColorStateList.valueOf(green));
    }

    private void enableButtons(boolean enabled) {
        btnAnswer1.setEnabled(enabled);
        btnAnswer2.setEnabled(enabled);
        btnAnswer3.setEnabled(enabled);
        btnAnswer4.setEnabled(enabled);
    }

    private MaterialButton getButton(int index) {
        switch (index) {
            case 0: return btnAnswer1;
            case 1: return btnAnswer2;
            case 2: return btnAnswer3;
            default: return btnAnswer4;
        }
    }

    private void updateScores() {
        tvMyScore.setText(String.valueOf(myScore));
        tvOpponentScore.setText(String.valueOf(opponentScore));
    }

    private void showError() {
        Toast.makeText(this, "Greska pri ucitavanju pitanja!", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
    }


}