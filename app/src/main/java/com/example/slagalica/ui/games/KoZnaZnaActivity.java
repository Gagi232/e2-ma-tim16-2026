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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.example.slagalica.data.model.KoZnaZnaQuestion;
import com.example.slagalica.data.repository.GameRepository;
import com.example.slagalica.data.repository.StatsRepository;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.logic.KoZnaZnaLogic;
import com.example.slagalica.logic.LeagueLogic;
import com.example.slagalica.logic.LeagueLogic;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KoZnaZnaActivity extends AppCompatActivity {

    private static final int COLOR_CORRECT = 0xFF4CAF50; // zelena
    private static final int COLOR_WRONG   = 0xFFF44336; // crvena
    private static final int COLOR_DEFAULT = 0xFF388E3C; // tamno zelena (default)

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

    private boolean isFriendly;

    private String matchId, myId, opponentId;
    private boolean isPlayer1, isGuest;
    private int correctCount = 0, wrongCount = 0;
    private DatabaseReference matchRef;

    // Da rezultat pitanja primenimo TAČNO JEDNOM (bez obzira ko prvi/drugi odgovori,
    // i bez obzira na redosled kojim Firebase okine listenere)
    private boolean[] resultApplied;

    // Da pokupimo i uklonimo sve listenere kad se Activity uništi (sprečava leak)
    private final List<DatabaseReference> activeRefs = new ArrayList<>();
    private final List<ValueEventListener> activeListeners = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ko_zna_zna);

        isGuest    = getIntent().getBooleanExtra("isGuest", false);
        matchId    = getIntent().getStringExtra("matchId");
        myId       = getIntent().getStringExtra("myId");
        opponentId = getIntent().getStringExtra("opponentId");
        isPlayer1  = getIntent().getBooleanExtra("isPlayer1", true);
        isFriendly = getIntent().getBooleanExtra("isFriendly", false); // NOVO - default false (random/rangirano)


        if (myId == null && !isGuest) {
            var fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (fbUser != null) myId = fbUser.getUid();
        }

        resultApplied = new boolean[KoZnaZnaLogic.TOTAL_QUESTIONS];

        bindViews();
        updateTopBar();
        findViewById(R.id.btnFinish).setVisibility(View.GONE);
        findViewById(R.id.btnLeave).setOnClickListener(v -> forfeit());

        if (isGuest || matchId == null) {
            loadSolo();
        } else {
            matchRef = FirebaseDatabase.getInstance().getReference("activeMatches").child(matchId).child("kzz");
            setupMultiplayer();
            listenForForfeit();
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

        if (matchRef != null) listenForAnswers(index);
    }

    private void startTimer() {
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(KoZnaZnaLogic.QUESTION_TIME_MS, 100) {
            @Override public void onTick(long ms) { tvTimer.setText(String.valueOf((ms / 1000) + 1)); }
            @Override public void onFinish() {
                tvTimer.setText("0");
                if (!answered) {
                    onTimeout();
                }
                if (matchRef != null) {
                    // Multiplayer: uvek čekamo da PRAVI tajmer od 5s istekne na oba telefona
                    // pre prelaska dalje, da oba klijenta ostanu sinhronizovana na istom pitanju.
                    new Handler().postDelayed(() -> showQuestion(currentIndex + 1), 1200);
                }
            }
        }.start();
    }

    private void onAnswered(int selectedIndex) {
        if (answered) return;
        answered = true;

        int correctIndex = questions.get(currentIndex).getCorrectIndex();
        boolean correct = (selectedIndex == correctIndex);
        if (correct) correctCount++; else wrongCount++;

        // Trenutna lična povratna informacija - ne čekamo protivnika za ovo
        showSelfFeedback(selectedIndex, correctIndex);

        if (matchRef != null) {
            writeAnswer(currentIndex, selectedIndex, correct);
            // NE prelazimo odmah na sledeće pitanje - sačekaćemo da istekne
            // zajednički tajmer (vidi startTimer -> onFinish), da ne sečemo
            // protivniku preostalo vreme za odgovor.
        } else {
            timer.cancel();
            myScore += logic.calcSoloScore(selectedIndex, correctIndex);
            updateScores();
            new Handler().postDelayed(() -> showQuestion(currentIndex + 1), 1500);
        }
    }

    private void onTimeout() {
        answered = true;
        if (matchRef != null) {
            writeAnswer(currentIndex, -1, false);
        }
        // solo timeout: bez poena, prelazak dalje se već zakazuje u onFinish
    }

    private void writeAnswer(int qIndex, int answerIndex, boolean correct) {
        Map<String, Object> data = new HashMap<>();
        data.put("answerIndex", answerIndex);
        data.put("correct", correct);
        // Vreme upisuje SAM SERVER, ne telefon - jer satovi na dva uređaja
        // nikad nisu savršeno sinhronizovani, a baš ovo vreme odlučuje ko je "brži".
        data.put("timestamp", ServerValue.TIMESTAMP);
        matchRef.child("answers").child(myId).child(String.valueOf(qIndex)).setValue(data);
    }

    // ── Real-time sinhronizacija rezultata ─────────────────────────────────

    private void listenForAnswers(final int qIndex) {
        DatabaseReference myAnsRef  = matchRef.child("answers").child(myId).child(String.valueOf(qIndex));
        DatabaseReference oppAnsRef = matchRef.child("answers").child(opponentId).child(String.valueOf(qIndex));

        // VAŽNO: slušamo OBE putanje (svoju i protivnikovu), ne samo protivnikovu.
        // Time je svejedno ko prvi a ko drugi odgovori - obojica će dobiti update.
        ValueEventListener trigger = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot ignored) {
                tryApplyResult(qIndex, myAnsRef, oppAnsRef);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };

        myAnsRef.addValueEventListener(trigger);
        oppAnsRef.addValueEventListener(trigger);

        activeRefs.add(myAnsRef);  activeListeners.add(trigger);
        activeRefs.add(oppAnsRef); activeListeners.add(trigger);
    }

    private void tryApplyResult(int qIndex, DatabaseReference myAnsRef, DatabaseReference oppAnsRef) {
        if (resultApplied[qIndex]) return;

        myAnsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot mySnap) {
                if (!mySnap.exists()) return; // ja još nisam odgovorio/timeout-ovao

                oppAnsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot oppSnap) {
                        if (!oppSnap.exists()) return; // protivnik još nije

                        // OBA odgovora postoje - sad konačno možemo da obračunamo poene
                        onBothAnswersReady(qIndex, mySnap, oppSnap);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void onBothAnswersReady(int qIndex, DataSnapshot mySnap, DataSnapshot oppSnap) {
        if (resultApplied[qIndex]) return; // dvostruka zaštita od duplog brojanja
        resultApplied[qIndex] = true;

        Boolean myCorrectObj  = mySnap.child("correct").getValue(Boolean.class);
        Boolean oppCorrectObj = oppSnap.child("correct").getValue(Boolean.class);
        Long myTsObj  = mySnap.child("timestamp").getValue(Long.class);
        Long oppTsObj = oppSnap.child("timestamp").getValue(Long.class);
        Integer myIdxObj  = mySnap.child("answerIndex").getValue(Integer.class);
        Integer oppIdxObj = oppSnap.child("answerIndex").getValue(Integer.class);

        int myIdx  = myIdxObj  != null ? myIdxObj  : -1;
        int oppIdx = oppIdxObj != null ? oppIdxObj : -1;

        int[] pts = logic.calcMultiScore(
                myIdx,  myCorrectObj  != null && myCorrectObj,  myTsObj  != null ? myTsObj  : Long.MAX_VALUE,
                oppIdx, oppCorrectObj != null && oppCorrectObj, oppTsObj != null ? oppTsObj : Long.MAX_VALUE);

        myScore += pts[0];
        opponentScore += pts[1];
        updateScores();

        // Vizuelni prikaz ko je šta odgovorio ima smisla samo ako smo i dalje
        // na tom istom pitanju (ako je kasno stiglo, poeni se svejedno računaju gore)
        if (qIndex == currentIndex) {
            int correctIndex = questions.get(qIndex).getCorrectIndex();
            applyFinalVisuals(myIdx, oppIdx, correctIndex);
        }
    }

    // ── Vizuelni prikaz odgovora ────────────────────────────────────────────

    /** Trenutna povratna informacija odmah pošto JA kliknem - bez čekanja protivnika. */
    private void showSelfFeedback(int selected, int correctIndex) {
        enableButtons(false);
        getButton(correctIndex).setBackgroundTintList(ColorStateList.valueOf(COLOR_CORRECT));
        if (selected != correctIndex && selected >= 0) {
            getButton(selected).setBackgroundTintList(ColorStateList.valueOf(COLOR_WRONG));
        }
    }

    /** Konačan prikaz pošto su OBA odgovora poznata - boje + labela ko je šta izabrao. */
    private void applyFinalVisuals(int myIndex, int oppIndex, int correctIndex) {
        enableButtons(false);
        List<String> opts = questions.get(currentIndex).getOptions();

        for (int i = 0; i < 4; i++) {
            MaterialButton btn = getButton(i);
            boolean isCorrect = (i == correctIndex);
            boolean mine = (i == myIndex);
            boolean opp  = (i == oppIndex);

            int color = isCorrect ? COLOR_CORRECT : (mine || opp ? COLOR_WRONG : COLOR_DEFAULT);
            btn.setBackgroundTintList(ColorStateList.valueOf(color));

            StringBuilder text = new StringBuilder(opts.get(i));
            if (mine && opp) text.append("\n👥 Ti i protivnik");
            else if (mine)   text.append("\n🧍 Tvoj odgovor");
            else if (opp)    text.append("\n👤 Protivnikov odgovor");
            btn.setText(text.toString());
        }
    }

    private void resetColors() {
        btnAnswer1.setBackgroundTintList(ColorStateList.valueOf(COLOR_DEFAULT));
        btnAnswer2.setBackgroundTintList(ColorStateList.valueOf(COLOR_DEFAULT));
        btnAnswer3.setBackgroundTintList(ColorStateList.valueOf(COLOR_DEFAULT));
        btnAnswer4.setBackgroundTintList(ColorStateList.valueOf(COLOR_DEFAULT));
    }

    private void enableButtons(boolean enabled) {
        btnAnswer1.setEnabled(enabled); btnAnswer2.setEnabled(enabled);
        btnAnswer3.setEnabled(enabled); btnAnswer4.setEnabled(enabled);
    }

    private MaterialButton getButton(int index) {
        return index == 0 ? btnAnswer1 : index == 1 ? btnAnswer2 : index == 2 ? btnAnswer3 : btnAnswer4;
    }

    private void updateScores() {
        tvMyScore.setText(String.valueOf(myScore));
        tvOpponentScore.setText(String.valueOf(opponentScore));
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

        new UserRepository().getCurrentUser(new UserRepository.Callback<User>() {
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


    // ── Kraj igre / izlaz ────────────────────────────────────────────────────

    private void endGame() {
        if (timer != null) timer.cancel();
        if (matchRef != null) {
            matchRef.child("finalScore").child(myId).setValue(myScore);
            matchRef.child("done").child(myId).setValue(true);
        }

        if (!isFriendly) {
            new StatsRepository().saveKoZnaZnaResult(correctCount, wrongCount, myScore, new StatsRepository.Callback<Void>() {
                @Override public void onSuccess(Void r) {}
                @Override public void onError(Exception e) {}
            });
        }



        Intent i = new Intent(this, SpojniceActivity.class);
        i.putExtra("isGuest", isGuest); i.putExtra("matchId", matchId);
        i.putExtra("myId", myId); i.putExtra("opponentId", opponentId);
        i.putExtra("isPlayer1", isPlayer1);
        i.putExtra("isFriendly", isFriendly); // NOVO - prosledi dalje
        i.putExtra("totalMyScore", myScore); i.putExtra("totalOpponentScore", opponentScore);
        startActivity(i); finish();
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
        intent.putExtra("totalMyScore", myScore);
        intent.putExtra("totalOpponentScore", 0);
        startActivity(intent);
        finish();
    }

    private void showError(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeForfeitListener();
        if (timer != null) timer.cancel();
        for (int i = 0; i < activeRefs.size(); i++) {
            activeRefs.get(i).removeEventListener(activeListeners.get(i));
        }
    }
}