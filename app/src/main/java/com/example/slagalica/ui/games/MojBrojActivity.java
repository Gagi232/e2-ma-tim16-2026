package com.example.slagalica.ui.games;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MojBrojActivity extends AppCompatActivity {
    private TextView tvTrazeniBroj;
    private TextView tvTimer;
    private final TextView[] tvBrojevi = new TextView[6];
    private final MaterialButton[] btnBrojevi = new MaterialButton[6];
    private TextInputEditText etIzraz;
    private final StringBuilder izraz = new StringBuilder();
    private int faza = 0; // 0=ceka prvi STOP, 1=ceka drugi STOP, 2=igra
    private ProgressBar pbTime;
    private CountDownTimer roundTimer;
    private CountDownTimer autoStopTimer;
    private final Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moj_broj);

        tvTrazeniBroj = findViewById(R.id.tvTrazeniBroj);
        tvTimer = findViewById(R.id.tvTimer);
        tvBrojevi[0] = findViewById(R.id.tvBroj1);
        tvBrojevi[1] = findViewById(R.id.tvBroj2);
        tvBrojevi[2] = findViewById(R.id.tvBroj3);
        tvBrojevi[3] = findViewById(R.id.tvBroj4);
        tvBrojevi[4] = findViewById(R.id.tvBroj5);
        tvBrojevi[5] = findViewById(R.id.tvBroj6);

        etIzraz = findViewById(R.id.etIzraz);
        pbTime = findViewById(R.id.pbTime);

        btnBrojevi[0] = findViewById(R.id.btnB1);
        btnBrojevi[1] = findViewById(R.id.btnB2);
        btnBrojevi[2] = findViewById(R.id.btnB3);
        btnBrojevi[3] = findViewById(R.id.btnB4);
        btnBrojevi[4] = findViewById(R.id.btnB5);
        btnBrojevi[5] = findViewById(R.id.btnB6);

        MaterialButton btnLeftParen = findViewById(R.id.btnLeftParen);
        MaterialButton btnRightParen = findViewById(R.id.btnRightParen);
        MaterialButton btnPlus = findViewById(R.id.btnPlus);
        MaterialButton btnMinus = findViewById(R.id.btnMinus);
        MaterialButton btnMul = findViewById(R.id.btnMul);
        MaterialButton btnDiv = findViewById(R.id.btnDiv);
        MaterialButton btnDelete = findViewById(R.id.btnDelete);
        MaterialButton btnReset = findViewById(R.id.btnReset);
        MaterialButton btnStop = findViewById(R.id.btnStop);
        MaterialButton btnPotvrdi = findViewById(R.id.btnPotvrdi);
        MaterialButton btnPredaj = findViewById(R.id.btnPredaj);
        MaterialButton btnNext = findViewById(R.id.btnNext);

        for (int i = 0; i < btnBrojevi.length; i++) {
            int index = i;
            btnBrojevi[i].setOnClickListener(v -> {
                if (faza != 2) return;
                String value = tvBrojevi[index].getText().toString();
                if (!"?".equals(value)) {
                    appendToExpression(value);
                }
            });
        }

        btnLeftParen.setOnClickListener(v -> appendOperator("("));
        btnRightParen.setOnClickListener(v -> appendOperator(")"));
        btnPlus.setOnClickListener(v -> appendOperator("+"));
        btnMinus.setOnClickListener(v -> appendOperator("-"));
        btnMul.setOnClickListener(v -> appendOperator("×"));
        btnDiv.setOnClickListener(v -> appendOperator("÷"));

        btnDelete.setOnClickListener(v -> {
            if (faza != 2 || izraz.length() == 0) return;
            izraz.deleteCharAt(izraz.length() - 1);
            updateExpression();
        });

        btnReset.setOnClickListener(v -> {
            if (faza != 2) return;
            izraz.setLength(0);
            updateExpression();
        });

        btnStop.setOnClickListener(v -> {
            if (faza == 0) {
                int target = 100 + random.nextInt(900);
                tvTrazeniBroj.setText(String.valueOf(target));
                faza = 1;
                startAutoStopTimer();
            } else if (faza == 1) {
                generateAndShowNumbers();
                faza = 2;
                btnStop.setEnabled(false);
                startRoundTimer();
            }
        });

        btnPotvrdi.setOnClickListener(v ->
                Toast.makeText(this, "(KT1) Validacija izraza", Toast.LENGTH_SHORT).show()
        );

        btnPredaj.setOnClickListener(v -> {
            startActivity(new Intent(this, KorakPoKorakActivity.class));
            finish();
        });

        btnNext.setOnClickListener(v -> {
            startActivity(new Intent(this, KorakPoKorakActivity.class));
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        if (roundTimer != null) roundTimer.cancel();
        if (autoStopTimer != null) autoStopTimer.cancel();
        super.onDestroy();
    }

    private void appendOperator(String op) {
        if (faza != 2) return;
        appendToExpression(op);
    }

    private void appendToExpression(String value) {
        izraz.append(value);
        updateExpression();
    }

    private void updateExpression() {
        etIzraz.setText(izraz.toString());
    }

    private void generateAndShowNumbers() {
        int[] numbers = generate6Numbers();
        for (int i = 0; i < 6; i++) {
            String value = String.valueOf(numbers[i]);
            tvBrojevi[i].setText(value);
            btnBrojevi[i].setText(value);
        }
    }

    private int[] generate6Numbers() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            list.add(1 + random.nextInt(9));
        }

        int[] tens = {10, 15, 20};
        list.add(tens[random.nextInt(tens.length)]);

        int[] large = {25, 50, 75, 100};
        list.add(large[random.nextInt(large.length)]);

        Collections.shuffle(list, random);
        int[] result = new int[6];
        for (int i = 0; i < 6; i++) {
            result[i] = list.get(i);
        }
        return result;
    }

    private void startRoundTimer() {
        if (roundTimer != null) roundTimer.cancel();
        pbTime.setMax(60);
        pbTime.setProgress(60);
        roundTimer = new CountDownTimer(60_000, 1_000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                pbTime.setProgress(seconds);
                tvTimer.setText(String.valueOf(seconds));
            }

            @Override
            public void onFinish() {
                pbTime.setProgress(0);
                faza = 3;
            }
        };
        roundTimer.start();
    }

    private void startAutoStopTimer() {
        if (autoStopTimer != null) autoStopTimer.cancel();
        autoStopTimer = new CountDownTimer(5_000, 1_000) {
            @Override
            public void onTick(long millisUntilFinished) {
                // no-op
            }

            @Override
            public void onFinish() {
                if (faza == 1) {
                    generateAndShowNumbers();
                    faza = 2;
                    startRoundTimer();
                }
            }
        };
        autoStopTimer.start();
    }
}