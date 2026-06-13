package com.example.slagalica.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.slagalica.R;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.UserRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etUsername, etPassword, etConfirmPassword;
    private AutoCompleteTextView actvRegion;
    private ProgressBar progressBar;
    private FirebaseAuth auth;
    private final UserRepository userRepo = new UserRepository();

    private static final String[] REGIONS = {
            "Vojvodina", "Beograd", "Šumadija",
            "Zapadna Srbija", "Istočna Srbija", "Južna Srbija"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();

        etEmail           = findViewById(R.id.etEmail);
        etUsername        = findViewById(R.id.etUsername);
        etPassword        = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        actvRegion        = findViewById(R.id.actvRegion);
        progressBar       = findViewById(R.id.progressBar);

        // Popuni dropdown za region
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, REGIONS);
        actvRegion.setAdapter(adapter);

        MaterialButton btnRegister = findViewById(R.id.btnRegister);
        TextView tvLogin = findViewById(R.id.tvLogin);

        btnRegister.setOnClickListener(v -> register());

        tvLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void register() {
        String email    = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String username = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
        String confirm  = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString().trim() : "";
        String region   = actvRegion.getText().toString().trim();

        // Validacija
        if (TextUtils.isEmpty(email)) { etEmail.setError("Unesite email"); return; }
        if (TextUtils.isEmpty(username)) { etUsername.setError("Unesite korisničko ime"); return; }
        if (TextUtils.isEmpty(region)) { actvRegion.setError("Izaberite region"); return; }
        if (TextUtils.isEmpty(password)) { etPassword.setError("Unesite lozinku"); return; }
        if (password.length() < 6) { etPassword.setError("Lozinka mora imati min. 6 karaktera"); return; }
        if (!password.equals(confirm)) { etConfirmPassword.setError("Lozinke se ne podudaraju"); return; }

        showLoading(true);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    String uid = result.getUser().getUid();

                    // Pošalji verifikacioni email
                    result.getUser().sendEmailVerification();

                    // Napravi User objekat i sačuvaj u Firestore
                    User user = new User();
                    user.setId(uid);
                    user.setUsername(username);
                    user.setEmail(email);
                    user.setRegion(region);
                    user.setTokens(5);
                    user.setStars(0);
                    user.setLeague(0);
                    user.setAvatarUrl("");

                    userRepo.saveUser(user, new UserRepository.Callback<Void>() {
                        @Override
                        public void onSuccess(Void r) {
                            showLoading(false);
                            Toast.makeText(RegisterActivity.this,
                                    "Registracija uspešna! Proverite email za potvrdu.",
                                    Toast.LENGTH_LONG).show();
                            auth.signOut();
                            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                            finish();
                        }

                        @Override
                        public void onError(Exception e) {
                            showLoading(false);
                            Toast.makeText(RegisterActivity.this,
                                    "Greška pri čuvanju podataka: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this,
                            "Greška: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void showLoading(boolean show) {
        if (progressBar != null)
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}