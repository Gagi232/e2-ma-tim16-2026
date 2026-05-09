package com.example.slagalica.ui.main;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.slagalica.R;
import com.example.slagalica.ui.games.KoZnaZnaActivity;
import com.google.android.material.button.MaterialButton;

public class GuestActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guest);

        MaterialButton btnPlay = findViewById(R.id.btnPlay);
        MaterialButton btnLogin = findViewById(R.id.btnLogin);

        btnPlay.setOnClickListener(v -> {
            Intent intent = new Intent(this, KoZnaZnaActivity.class);
            intent.putExtra("isGuest", true);
            startActivity(intent);
        });

        btnLogin.setOnClickListener(v -> {
            startActivity(new Intent(this,
                    com.example.slagalica.ui.auth.LoginActivity.class));
            finish();
        });
    }
}