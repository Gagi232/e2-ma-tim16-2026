package com.example.slagalica.ui.main;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalica.MainActivity;
import com.example.slagalica.R;
import com.google.android.material.button.MaterialButton;

public class ResultsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_results);

        boolean isGuest = getIntent().getBooleanExtra("isGuest", false);

        MaterialButton btnHome = findViewById(R.id.btnHome);
        btnHome.setOnClickListener(v -> {
            if (isGuest) {
                Intent intent = new Intent(this, GuestActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            } else {
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }
            finish();
        });
    }
}