package com.example.slagalica.ui.games;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.slagalica.MainActivity;
import com.example.slagalica.R;
import com.google.android.material.button.MaterialButton;

public class KoZnaZnaActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ko_zna_zna);

        MaterialButton btnFinish = findViewById(R.id.btnFinish);
        btnFinish.setOnClickListener(v -> {
            Intent intent = new Intent(this, SpojniceActivity.class);
            intent.putExtra("isGuest", getIntent().getBooleanExtra("isGuest", false));
            startActivity(intent);
            finish();
        });
        MaterialButton btnPredaj = findViewById(R.id.btnLeave);
        btnPredaj.setOnClickListener(v -> {
            boolean isGuest = getIntent().getBooleanExtra("isGuest", false);
            Intent intent;

            if (isGuest) {
                intent = new Intent(this, com.example.slagalica.ui.main.GuestActivity.class);
                intent.putExtra("isGuest", true);
            } else {
                intent = new Intent(this, com.example.slagalica.MainActivity.class);
                intent.putExtra("isGuest", false);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

            startActivity(intent);
            finish();
        });

    }
}