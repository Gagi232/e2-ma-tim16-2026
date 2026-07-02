package com.example.slagalica.ui.challenge;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.example.slagalica.R;
import com.example.slagalica.data.repository.ChallengeRepository;
import com.example.slagalica.ui.games.*;
import com.google.firebase.auth.FirebaseAuth;

public class ChallengeGameActivity extends AppCompatActivity {

    private static final Class<?>[] GAME_ORDER = {
            KoZnaZnaActivity.class,
            SpojniceActivity.class,
            AsocijacijeActivity.class,
            SkockoActivity.class,
            KorakPoKorakActivity.class,
            MojBrojActivity.class
    };

    private String challengeId, myUid;
    private int starsWager, tokensWager;
    private int currentGameIndex = 0;
    private int totalScore = 0;

    private TextView tvCurrentGame, tvTotalScore;

    private final ActivityResultLauncher<Intent> gameLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            int gameScore = result.getData().getIntExtra("gameScore", 0);
                            totalScore = gameScore;
                            updateUI();
                        }
                        currentGameIndex++;
                        launchNextGame();
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_game);

        challengeId  = getIntent().getStringExtra("challengeId");
        starsWager   = getIntent().getIntExtra("starsWager", 0);
        tokensWager  = getIntent().getIntExtra("tokensWager", 0);
        myUid = FirebaseAuth.getInstance().getUid();

        tvCurrentGame = findViewById(R.id.tvCurrentGame);
        tvTotalScore  = findViewById(R.id.tvTotalScore);

        launchNextGame();
    }

    private void launchNextGame() {
        if (currentGameIndex >= GAME_ORDER.length) {
            submitAndFinish();
            return;
        }

        Class<?> gameClass = GAME_ORDER[currentGameIndex];
        if (tvCurrentGame != null)
            tvCurrentGame.setText("Igra " + (currentGameIndex + 1) + "/6");

        Intent i = new Intent(this, gameClass);
        i.putExtra("isGuest", false);
        i.putExtra("matchId", (String) null);
        i.putExtra("myId", myUid);
        i.putExtra("opponentId", (String) null);
        i.putExtra("isPlayer1", true);
        i.putExtra("isFriendly", false);
        i.putExtra("isChallengeMode", true);
        i.putExtra("totalMyScore", totalScore);
        i.putExtra("totalOpponentScore", 0);
        gameLauncher.launch(i);
    }

    private void updateUI() {
        if (tvTotalScore != null)
            tvTotalScore.setText("Ukupno: " + totalScore);
    }

    private void submitAndFinish() {
        new ChallengeRepository().submitScore(challengeId, myUid, totalScore,
                new ChallengeRepository.Callback<Void>() {
                    @Override
                    public void onSuccess(Void r) {
                        Intent i = new Intent(ChallengeGameActivity.this,
                                ChallengeResultActivity.class);
                        i.putExtra("challengeId", challengeId);
                        i.putExtra("myScore", totalScore);
                        i.putExtra("starsWager", starsWager);
                        i.putExtra("tokensWager", tokensWager);
                        startActivity(i);
                        finish();
                    }
                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(ChallengeGameActivity.this,
                                "Greška pri upisu rezultata.", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}