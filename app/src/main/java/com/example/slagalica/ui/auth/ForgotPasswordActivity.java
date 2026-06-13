package com.example.slagalica.ui.auth;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.slagalica.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends AppCompatActivity {

    private TextInputEditText etOldPassword, etNewPassword, etConfirmNewPassword;
    private ProgressBar progressBar;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        auth = FirebaseAuth.getInstance();

        etOldPassword        = findViewById(R.id.etOldPassword);
        etNewPassword        = findViewById(R.id.etNewPassword);
        etConfirmNewPassword = findViewById(R.id.etConfirmNewPassword);
        progressBar          = findViewById(R.id.progressBar);

        MaterialButton btnReset = findViewById(R.id.btnResetPassword);

        btnReset.setOnClickListener(v -> resetPassword());

        findViewById(R.id.ivBack).setOnClickListener(v -> finish());
    }

    private void resetPassword() {
        String oldPass    = etOldPassword.getText() != null ? etOldPassword.getText().toString().trim() : "";
        String newPass    = etNewPassword.getText() != null ? etNewPassword.getText().toString().trim() : "";
        String confirmPass = etConfirmNewPassword.getText() != null ? etConfirmNewPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(oldPass)) { etOldPassword.setError("Unesite staru lozinku"); return; }
        if (TextUtils.isEmpty(newPass)) { etNewPassword.setError("Unesite novu lozinku"); return; }
        if (newPass.length() < 6) { etNewPassword.setError("Min. 6 karaktera"); return; }
        if (!newPass.equals(confirmPass)) { etConfirmNewPassword.setError("Lozinke se ne podudaraju"); return; }

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Niste ulogovani!", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        // Prvo re-autentifikuj sa starom lozinkom
        String email = auth.getCurrentUser().getEmail();
        com.google.firebase.auth.AuthCredential credential =
                com.google.firebase.auth.EmailAuthProvider.getCredential(email, oldPass);

        auth.getCurrentUser().reauthenticate(credential)
                .addOnSuccessListener(v -> {
                    // Pa onda promeni lozinku
                    auth.getCurrentUser().updatePassword(newPass)
                            .addOnSuccessListener(v2 -> {
                                showLoading(false);
                                Toast.makeText(this,
                                        "Lozinka uspešno promenjena!",
                                        Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                showLoading(false);
                                Toast.makeText(this,
                                        "Greška: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this,
                            "Stara lozinka nije tačna!",
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void showLoading(boolean show) {
        if (progressBar != null)
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}