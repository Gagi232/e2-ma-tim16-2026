package com.example.slagalica.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.slagalica.R;
import com.example.slagalica.ui.auth.LoginActivity;
import com.google.android.material.button.MaterialButton;

public class ProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        MaterialButton btnLogout = findViewById(R.id.btnLogout);
        ImageView ivChangeAvatar = findViewById(R.id.ivChangeAvatar);
        ImageView btnBack = findViewById(R.id.btnBack);

        btnLogout.setOnClickListener(v -> {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        ivChangeAvatar.setOnClickListener(v -> showAvatarDialog());

        btnBack.setOnClickListener(v -> finish());
    }

    private void showAvatarDialog() {
        String[] avatars = {"👤 Default", "🦁 Lav", "🐯 Tigar",
                "🦊 Lisica", "🐺 Vuk", "🐻 Medved",
                "🐼 Panda", "🦝 Rakun"};

        new AlertDialog.Builder(this)
                .setTitle("Izaberi avatar")
                .setItems(avatars, (dialog, which) ->
                        Toast.makeText(this,
                                "Avatar promenjen na " + avatars[which],
                                Toast.LENGTH_SHORT).show()
                )
                .show();
    }
}