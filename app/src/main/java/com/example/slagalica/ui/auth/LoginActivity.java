package com.example.slagalica.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.slagalica.MainActivity;
import com.example.slagalica.R;
import com.example.slagalica.ui.main.GuestActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword;
    private TextInputLayout tilEmail;
    private ProgressBar progressBar;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();

        FirebaseUser current = auth.getCurrentUser();
        if (current != null && current.isEmailVerified()) {
            goToMain();
            return;
        }

        tilEmail      = findViewById(R.id.tilEmail);
        etEmail       = findViewById(R.id.etEmail);
        etPassword    = findViewById(R.id.etPassword);
        progressBar   = findViewById(R.id.progressBar);

        tilEmail.setHint("Email ili korisničko ime");

        MaterialButton btnLogin            = findViewById(R.id.btnLogin);
        MaterialButton btnGuest            = findViewById(R.id.btnGuest);
        TextView       tvRegister          = findViewById(R.id.tvRegister);
        TextView       tvForgotPassword    = findViewById(R.id.tvForgotPassword);
        TextView       tvResendVerification= findViewById(R.id.tvResendVerification);

        btnLogin.setOnClickListener(v -> login());

        btnGuest.setOnClickListener(v -> {
            startActivity(new Intent(this, GuestActivity.class));
            finish();
        });

        tvRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class))
        );

        tvForgotPassword.setOnClickListener(v ->
                startActivity(new Intent(this, ForgotPasswordActivity.class))
        );

        tvResendVerification.setOnClickListener(v -> resendVerificationEmail());
    }

    private void login() {
        String input    = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(input)) {
            etEmail.setError("Unesite email ili korisničko ime");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Unesite lozinku");
            return;
        }

        showLoading(true);

        if (input.contains("@")) {
            // Direktan email login
            performLogin(input, password);
        } else {
            // Pretraga korisnika po korisničkom imenu
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .whereEqualTo("username", input)
                    .limit(1)
                    .get()
                    .addOnSuccessListener(query -> {
                        if (!query.isEmpty()) {
                            String email = query.getDocuments().get(0).getString("email");
                            if (email != null) {
                                performLogin(email, password);
                            } else {
                                showLoading(false);
                                etEmail.setError("Korisničko ime nije pronađeno");
                            }
                        } else {
                            showLoading(false);
                            etEmail.setError("Korisničko ime nije pronađeno");
                        }
                    })
                    .addOnFailureListener(e -> {
                        showLoading(false);
                        Toast.makeText(this, "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void performLogin(String email, String password) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user != null && user.isEmailVerified()) {
                        showLoading(false);
                        goToMain();
                    } else {
                        showLoading(false);
                        Toast.makeText(this,
                                "Molimo potvrdite email pre prijave!",
                                Toast.LENGTH_LONG).show();
                        auth.signOut();
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this,
                            "Pogrešna lozinka ili nalog ne postoji.",
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void resendVerificationEmail() {
        String input    = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        if (input.isEmpty() || password.isEmpty()) {
            Toast.makeText(this,
                    "Unesite email i lozinku pa kliknite ponovo.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        showLoading(true);

        // Morate biti privremeno ulogovani da biste poslali verifikacioni email
        String emailToUse = input.contains("@") ? input : null;

        if (emailToUse == null) {
            // Pretraga emaila po korisničkom imenu
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users")
                    .whereEqualTo("username", input)
                    .limit(1)
                    .get()
                    .addOnSuccessListener(query -> {
                        if (!query.isEmpty()) {
                            String email = query.getDocuments().get(0).getString("email");
                            if (email != null) sendVerificationAfterLogin(email, password);
                            else { showLoading(false); Toast.makeText(this, "Korisnik nije pronađen.", Toast.LENGTH_SHORT).show(); }
                        } else {
                            showLoading(false);
                            Toast.makeText(this, "Korisnik nije pronađen.", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> { showLoading(false); });
        } else {
            sendVerificationAfterLogin(emailToUse, password);
        }
    }

    private void sendVerificationAfterLogin(String email, String password) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user != null && !user.isEmailVerified()) {
                        user.sendEmailVerification()
                                .addOnSuccessListener(v -> {
                                    showLoading(false);
                                    Toast.makeText(this,
                                            "Verifikacioni email je ponovo poslat. Proverite spam folder!",
                                            Toast.LENGTH_LONG).show();
                                })
                                .addOnFailureListener(e -> {
                                    showLoading(false);
                                    Toast.makeText(this, "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                    } else if (user != null && user.isEmailVerified()) {
                        showLoading(false);
                        Toast.makeText(this, "Email je već verifikovan! Možete se ulogovati.", Toast.LENGTH_LONG).show();
                    }
                    if (user != null) auth.signOut();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Pogrešna lozinka.", Toast.LENGTH_SHORT).show();
                });
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void showLoading(boolean show) {
        if (progressBar != null)
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}