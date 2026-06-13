package com.example.slagalica.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.slagalica.R;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.ui.auth.LoginActivity;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileActivity extends AppCompatActivity {

    private static final String[] AVATAR_OPTIONS = {"👤", "🦁", "🐯", "🦊", "🐺", "🐻", "🐼", "🦝"};
    private static final String[] AVATAR_LABELS  = {"Default", "Lav", "Tigar", "Lisica", "Vuk", "Medved", "Panda", "Rakun"};
    private static final String[] LEAGUE_NAMES   = {"Početnik", "Bronze", "Silver", "Gold", "Platinum", "Diamond"};
    private static final String[] LEAGUE_ICONS   = {"⚪", "🥉", "🥈", "🥇", "💎", "👑"};

    private final UserRepository userRepo = new UserRepository();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private TextView tvUsername, tvEmail, tvTokens, tvStars, tvLeague, tvRegion;
    private TextView tvAvatar; // <-- ovo je novi TextView za emoji avatar
    private TextView tvTotalGames;
    private ProgressBar pbWinRate, pbKoZnaZna, pbSpojnice;
    private TextView tvWinRate, tvKoZnaZnaStats, tvKoZnaZnaAvg;
    private TextView tvSpojniceStats, tvSpojniceAvg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        initViews();
        loadUserData();

        findViewById(R.id.ivChangeAvatar).setOnClickListener(v -> showAvatarDialog());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        ((MaterialButton) findViewById(R.id.btnLogout)).setOnClickListener(v -> logout());
    }

    private void initViews() {
        tvAvatar      = findViewById(R.id.ivAvatar); // sad je TextView
        tvUsername    = findViewById(R.id.tvUsername);
        tvEmail       = findViewById(R.id.tvEmail);
        tvTokens      = findViewById(R.id.tvTokens);
        tvStars       = findViewById(R.id.tvStars);
        tvLeague      = findViewById(R.id.tvLeague);
        tvRegion      = findViewById(R.id.tvRegion);

        tvTotalGames     = findViewById(R.id.tvTotalGames);
        pbWinRate        = findViewById(R.id.pbWinRate);
        tvWinRate        = findViewById(R.id.tvWinRate);
        pbKoZnaZna       = findViewById(R.id.pbKoZnaZna);
        tvKoZnaZnaStats  = findViewById(R.id.tvKoZnaZnaStats);
        tvKoZnaZnaAvg    = findViewById(R.id.tvKoZnaZnaAvg);
        pbSpojnice       = findViewById(R.id.pbSpojnice);
        tvSpojniceStats  = findViewById(R.id.tvSpojniceStats);
        tvSpojniceAvg    = findViewById(R.id.tvSpojniceAvg);
    }

    private void loadUserData() {
        userRepo.getCurrentUser(new UserRepository.Callback<User>() {
            @Override
            public void onSuccess(User user) {
                displayUser(user);
                loadStats(user.getId());
            }
            @Override
            public void onError(Exception e) {
                Toast.makeText(ProfileActivity.this,
                        "Greška pri učitavanju profila", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displayUser(User user) {
        // Avatar emoji
        if (tvAvatar != null) {
            String avatar = user.getAvatarUrl();
            tvAvatar.setText(avatar != null && !avatar.isEmpty() ? avatar : "👤");
        }

        tvUsername.setText(user.getUsername());
        tvEmail.setText(user.getEmail());
        tvTokens.setText(String.valueOf(user.getTokens()));
        tvStars.setText(String.valueOf(user.getStars()));

        int league = Math.max(0, Math.min(user.getLeague(), LEAGUE_NAMES.length - 1));
        tvLeague.setText(LEAGUE_ICONS[league] + " " + LEAGUE_NAMES[league]);
        tvRegion.setText(user.getRegion() != null ? "📍 " + user.getRegion() : "📍 —");
    }

    private void loadStats(String uid) {
        db.collection("stats").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) applyStats(doc);
                });
    }

    private void applyStats(DocumentSnapshot doc) {
        long totalGames      = getLong(doc, "totalGames");
        long wins            = getLong(doc, "wins");
        long kzzCorrect      = getLong(doc, "kzzCorrect");
        long kzzWrong        = getLong(doc, "kzzWrong");
        long spojniceCorrect = getLong(doc, "spojniceCorrect");
        long spojniceTotal   = getLong(doc, "spojniceTotal");
        long kzzAvgMin       = getLong(doc, "kzzAvgMin");
        long kzzAvgMax       = getLong(doc, "kzzAvgMax");
        long spojniceAvgMin  = getLong(doc, "spojniceAvgMin");
        long spojniceAvgMax  = getLong(doc, "spojniceAvgMax");

        tvTotalGames.setText(String.valueOf(totalGames));

        int winPct = totalGames > 0 ? (int)(wins * 100 / totalGames) : 0;
        pbWinRate.setProgress(winPct);
        tvWinRate.setText(winPct + "%");

        long kzzTotal = kzzCorrect + kzzWrong;
        int kzzPct = kzzTotal > 0 ? (int)(kzzCorrect * 100 / kzzTotal) : 0;
        pbKoZnaZna.setProgress(kzzPct);
        tvKoZnaZnaStats.setText(kzzCorrect + "/" + kzzWrong + " ✓/✗");
        tvKoZnaZnaAvg.setText("Prosek bodova: " + kzzAvgMin + "-" + kzzAvgMax);

        int spojnicePct = spojniceTotal > 0 ? (int)(spojniceCorrect * 100 / spojniceTotal) : 0;
        pbSpojnice.setProgress(spojnicePct);
        tvSpojniceStats.setText(spojnicePct + "%");
        tvSpojniceAvg.setText("Prosek bodova: " + spojniceAvgMin + "-" + spojniceAvgMax);
    }

    private long getLong(DocumentSnapshot doc, String field) {
        Long v = doc.getLong(field);
        return v != null ? v : 0L;
    }

    private void showAvatarDialog() {
        String[] items = new String[AVATAR_OPTIONS.length];
        for (int i = 0; i < items.length; i++)
            items[i] = AVATAR_OPTIONS[i] + "  " + AVATAR_LABELS[i];

        new AlertDialog.Builder(this)
                .setTitle("Izaberi avatar")
                .setItems(items, (dlg, which) -> {
                    String chosen = AVATAR_OPTIONS[which];
                    userRepo.updateAvatar(chosen, new UserRepository.Callback<Void>() {
                        @Override
                        public void onSuccess(Void r) {
                            // Odmah ažuriraj UI
                            if (tvAvatar != null) tvAvatar.setText(chosen);
                            Toast.makeText(ProfileActivity.this,
                                    "Avatar promenjen na " + chosen,
                                    Toast.LENGTH_SHORT).show();
                        }
                        @Override
                        public void onError(Exception e) {
                            Toast.makeText(ProfileActivity.this,
                                    "Greška pri promeni avatara",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .show();
    }

    private void logout() {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}