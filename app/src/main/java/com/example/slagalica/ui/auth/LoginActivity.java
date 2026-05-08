package com.example.slagalica.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.slagalica.R;
import com.example.slagalica.MainActivity;
import com.google.android.material.button.MaterialButton;
import android.widget.TextView;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        MaterialButton btnLogin = findViewById(R.id.btnLogin);
        MaterialButton btnGuest = findViewById(R.id.btnGuest);
        TextView tvRegister = findViewById(R.id.tvRegister);
        TextView tvForgotPassword = findViewById(R.id.tvForgotPassword);

        // Prijava → MainActivity
        btnLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        // Gost → MainActivity
        btnGuest.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        // Registracija
        tvRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class))
        );

        // Zaboravljena lozinka
        tvForgotPassword.setOnClickListener(v ->
                startActivity(new Intent(this, ForgotPasswordActivity.class))
        );
    }
}