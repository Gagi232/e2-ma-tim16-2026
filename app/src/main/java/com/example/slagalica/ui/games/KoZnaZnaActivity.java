package com.example.slagalica.ui.games;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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

    private TextView tvQuestion, tvTimer, tvMyScore, tvOpponentScore, tvQuestionNumber;
    private MaterialButton btnAnswer1, btnAnswer2, btnAnswer3, btnAnswer4;

    private final KoZnaZnaLogic logic = new KoZnaZnaLogic();
    private final GameRepository gameRepo = new GameRepository();
    private List<KoZnaZnaQuestion> questions = new ArrayList<>();

    private int currentIndex = 0;
    private int myScore = 0;
    private int opponentScore = 0;
    private boolean answered = false;
    private CountDownTimer timer;

    private String matchId, myId, opponentId;
    private boolean isPlayer1, isGuest;
    private int correctCount = 0, wrongCount = 0;
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

        if (myId == null && !isGuest) {
            var fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (fbUser != null) myId = fbUser.getUid();
        }

        bindViews();
        findViewById(R.id.btnFinish).setVisibility(View.GONE);
        findViewById(R.id.btnLeave).setOnClickListener(v -> forfeit());

        if (isGuest || matchId == null) {
            loadSolo();
        } else {
            matchRef = FirebaseDatabase.getInstance().getReference("activeMatches").child(matchId).child("kzz");
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

    private void loadSolo() {
        gameRepo.getRandomKoZnaZnaQuestions(KoZnaZnaLogic.TOTAL_QUESTIONS, result -> {
            questions = result;
            showQuestion(0);
        }, e -> showError("Greška pri učitavanju solo pitanja"));
    }

    private void setupMultiplayer() {
        if (isPlayer1) {
            gameRepo.getRandomKoZnaZnaQuestions(KoZnaZnaLogic.TOTAL_QUESTIONS, result -> {
                questions = result;
                List<String> ids = new ArrayList<>();
                for (KoZnaZnaQuestion q : result) ids.add(q.getId());
                matchRef.child("questionIds").setValue(ids).addOnSuccessListener(v -> showQuestion(0));
            }, e -> showError("Greška pri dobavljanju pitanja (P1)"));
        } else {
            waitForQuestionIds();
        }
    }

    private void waitForQuestionIds() {
        matchRef.child("questionIds").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    new Handler().postDelayed(() -> waitForQuestionIds(), 1000);
                    return;
                }
                List<String> ids = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    ids.add(child.getValue(String.class));
                }
                if (ids.isEmpty()) {
                    new Handler().postDelayed(() -> waitForQuestionIds(), 1000);
                    return;
                }
                gameRepo.getQuestionsByIds(ids, result -> {
                    questions = result;
                    if (questions.isEmpty()) showError("Pitanja nisu pronađena u bazi!");
                    else showQuestion(0);
                }, e -> showError("Greška pri učitavanju pitanja po ID-u"));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { showError("Firebase greška: " + error.getMessage()); }
        });
    }

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
        btnAnswer1.setText(opts.get(0)); btnAnswer2.setText(opts.get(1));
        btnAnswer3.setText(opts.get(2)); btnAnswer4.setText(opts.get(3));

        resetColors();
        enableButtons(true);
        updateScores();
        startTimer();

        if (matchRef != null) listenForOpponentAnswer(index);
    }

    private void startTimer() {
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(KoZnaZnaLogic.QUESTION_TIME_MS, 100) {
            @Override public void onTick(long ms) { tvTimer.setText(String.valueOf((ms / 1000) + 1)); }
            @Override public void onFinish() {
                tvTimer.setText("0");
                if (!answered) onTimeout();
            }
        }.start();
    }

    private void onAnswered(int selectedIndex) {
        if (answered) return;
        answered = true;
        if (timer != null) timer.cancel();

        long ts = System.currentTimeMillis();
        boolean correct = (selectedIndex == questions.get(currentIndex).getCorrectIndex());
        if (correct) correctCount++; else wrongCount++;
        showFeedback(selectedIndex, questions.get(currentIndex).getCorrectIndex());

        if (matchRef != null) {
            writeAnswer(selectedIndex, correct, ts);
        } else {
            myScore += logic.calcSoloScore(selectedIndex, questions.get(currentIndex).getCorrectIndex());
            updateScores();
        }
        new Handler().postDelayed(() -> showQuestion(currentIndex + 1), 1500);
    }

    private void onTimeout() {
        answered = true;
        if (matchRef != null) writeAnswer(-1, false, -1L);
        new Handler().postDelayed(() -> showQuestion(currentIndex + 1), 500);
    }

    private void writeAnswer(int index, boolean correct, long ts) {
        Map<String, Object> data = new HashMap<>();
        data.put("answerIndex", index); data.put("correct", correct); data.put("timestamp", ts);
        matchRef.child("answers").child(myId).child(String.valueOf(currentIndex)).setValue(data);
    }

    private void listenForOpponentAnswer(int qIndex) {
        matchRef.child("answers").child(opponentId).child(String.valueOf(qIndex))
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot oppSnapshot) {
                        if (!oppSnapshot.exists()) return;
                        matchRef.child("answers").child(myId).child(String.valueOf(qIndex))
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot mySnapshot) {
                                        if (!mySnapshot.exists()) return;
                                        applyMultiplayerScore(mySnapshot, oppSnapshot);
                                    }
                                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                                });
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void applyMultiplayerScore(DataSnapshot mySnap, DataSnapshot oppSnap) {
        Boolean myC = mySnap.child("correct").getValue(Boolean.class);
        Boolean oppC = oppSnap.child("correct").getValue(Boolean.class);
        Long myT = mySnap.child("timestamp").getValue(Long.class);
        Long oppT = oppSnap.child("timestamp").getValue(Long.class);
        Integer myI = mySnap.child("answerIndex").getValue(Integer.class);
        Integer oppI = oppSnap.child("answerIndex").getValue(Integer.class);

        int[] pts = logic.calcMultiScore(
                myI != null ? myI : -1, myC != null && myC, myT != null ? myT : Long.MAX_VALUE,
                oppI != null ? oppI : -1, oppC != null && oppC, oppT != null ? oppT : Long.MAX_VALUE);
        
        myScore += pts[0];
        opponentScore += pts[1];
        updateScores();
    }

    private void endGame() {
        if (timer != null) timer.cancel();
        if (matchRef != null) {
            matchRef.child("finalScore").child(myId).setValue(myScore);
            matchRef.child("done").child(myId).setValue(true);
        }
        new StatsRepository().saveKoZnaZnaResult(correctCount, wrongCount, myScore, new StatsRepository.Callback<Void>() {
            @Override public void onSuccess(Void r) {}
            @Override public void onError(Exception e) {}
        });

        Intent i = new Intent(this, SpojniceActivity.class);
        i.putExtra("isGuest", isGuest); i.putExtra("matchId", matchId);
        i.putExtra("myId", myId); i.putExtra("opponentId", opponentId);
        i.putExtra("isPlayer1", isPlayer1);
        i.putExtra("kzzMyScore", myScore); i.putExtra("kzzOpponentScore", opponentScore);
        startActivity(i); finish();
    }

    private void forfeit() {
        if (timer != null) timer.cancel();
        if (matchRef != null) matchRef.getParent().child("status").setValue("forfeit_" + myId);
        finish();
    }

    private void showFeedback(int selected, int correct) {
        enableButtons(false);
        getButton(correct).setBackgroundTintList(ColorStateList.valueOf(0xFF4CAF50));
        if (selected != correct && selected >= 0) getButton(selected).setBackgroundTintList(ColorStateList.valueOf(0xFFF44336));
    }

    private void resetColors() {
        int green = 0xFF388E3C;
        btnAnswer1.setBackgroundTintList(ColorStateList.valueOf(green));
        btnAnswer2.setBackgroundTintList(ColorStateList.valueOf(green));
        btnAnswer3.setBackgroundTintList(ColorStateList.valueOf(green));
        btnAnswer4.setBackgroundTintList(ColorStateList.valueOf(green));
    }

    private void enableButtons(boolean enabled) {
        btnAnswer1.setEnabled(enabled); btnAnswer2.setEnabled(enabled);
        btnAnswer3.setEnabled(enabled); btnAnswer4.setEnabled(enabled);
    }

    private MaterialButton getButton(int index) {
        return index==0?btnAnswer1:index==1?btnAnswer2:index==2?btnAnswer3:btnAnswer4;
    }

    private void updateScores() {
        tvMyScore.setText(String.valueOf(myScore));
        tvOpponentScore.setText(String.valueOf(opponentScore));
    }

    private void showError(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
    }
}