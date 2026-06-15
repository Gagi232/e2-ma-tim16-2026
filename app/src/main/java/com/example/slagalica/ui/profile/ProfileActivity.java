// ui/profile/ProfileActivity.java
package com.example.slagalica.ui.profile;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.slagalica.R;
import com.example.slagalica.data.model.PlayerStats;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.StatsRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.logic.LeagueLogic;
import com.example.slagalica.ui.auth.LoginActivity;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

public class ProfileActivity extends AppCompatActivity {

    private static final String[] AVATAR_OPTIONS = {"👤","🦁","🐯","🦊","🐺","🐻","🐼","🦝"};
    private static final String[] AVATAR_LABELS  = {"Default","Lav","Tigar","Lisica","Vuk","Medved","Panda","Rakun"};

    private final UserRepository  userRepo  = new UserRepository();
    private final StatsRepository statsRepo = new StatsRepository();

    // Header
    private TextView  tvAvatar, tvUsername, tvEmail, tvTokens, tvStars, tvLeague, tvRegion;
    private ImageView ivQrCode;

    // Statistika
    private TextView  tvTotalGames;
    private ProgressBar pbWinRate;        private TextView tvWinRate;
    private ProgressBar pbKoZnaZna;       private TextView tvKoZnaZnaStats, tvKoZnaZnaAvg;
    private ProgressBar pbSpojnice;       private TextView tvSpojniceStats,  tvSpojniceAvg;
    private ProgressBar pbAsocijacije;    private TextView tvAsocijacijeStats, tvAsocijacijeAvg;
    private ProgressBar pbSkocko;         private TextView tvSkockoStats,    tvSkockoAvg;
    private ProgressBar pbKorakPoKorak;   private TextView tvKorakStats,     tvKorakAvg;
    private ProgressBar pbMojBroj;        private TextView tvMojBrojStats,   tvMojBrojAvg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        initViews();
        //loadAll();
        findViewById(R.id.ivChangeAvatar).setOnClickListener(v -> showAvatarDialog());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        ((MaterialButton) findViewById(R.id.btnLogout)).setOnClickListener(v -> logout());
    }

    private void initViews() {
        tvAvatar         = findViewById(R.id.ivAvatar);
        tvUsername       = findViewById(R.id.tvUsername);
        tvEmail          = findViewById(R.id.tvEmail);
        tvTokens         = findViewById(R.id.tvTokens);
        tvStars          = findViewById(R.id.tvStars);
        tvLeague         = findViewById(R.id.tvLeague);
        tvRegion         = findViewById(R.id.tvRegion);
        ivQrCode         = findViewById(R.id.ivQrCode);

        tvTotalGames     = findViewById(R.id.tvTotalGames);

        pbWinRate        = findViewById(R.id.pbWinRate);
        tvWinRate        = findViewById(R.id.tvWinRate);

        pbKoZnaZna       = findViewById(R.id.pbKoZnaZna);
        tvKoZnaZnaStats  = findViewById(R.id.tvKoZnaZnaStats);
        tvKoZnaZnaAvg    = findViewById(R.id.tvKoZnaZnaAvg);

        pbSpojnice       = findViewById(R.id.pbSpojnice);
        tvSpojniceStats  = findViewById(R.id.tvSpojniceStats);
        tvSpojniceAvg    = findViewById(R.id.tvSpojniceAvg);

        pbAsocijacije    = findViewById(R.id.pbAsocijacije);
        tvAsocijacijeStats = findViewById(R.id.tvAsocijacijeStats);
        tvAsocijacijeAvg   = findViewById(R.id.tvAsocijacijeAvg);

        pbSkocko         = findViewById(R.id.pbSkocko);
        tvSkockoStats    = findViewById(R.id.tvSkockoStats);
        tvSkockoAvg      = findViewById(R.id.tvSkockoAvg);

        pbKorakPoKorak   = findViewById(R.id.pbKorakPoKorak);
        tvKorakStats     = findViewById(R.id.tvKorakStats);
        tvKorakAvg       = findViewById(R.id.tvKorakAvg);

        pbMojBroj        = findViewById(R.id.pbMojBroj);
        tvMojBrojStats   = findViewById(R.id.tvMojBrojStats);
        tvMojBrojAvg     = findViewById(R.id.tvMojBrojAvg);
    }

    private void loadAll() {
        userRepo.getCurrentUser(new UserRepository.Callback<User>() {
            @Override public void onSuccess(User user) {
                displayUser(user);
                generateQrCode(user.getId());
                loadStats();
            }
            @Override public void onError(Exception e) {
                Toast.makeText(ProfileActivity.this,
                        "Greška pri učitavanju profila", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displayUser(User user) {
        String avatar = user.getAvatarUrl();
        tvAvatar.setText(avatar != null && !avatar.isEmpty() ? avatar : "👤");
        tvUsername.setText(user.getUsername());
        tvEmail.setText(user.getEmail());
        tvTokens.setText(String.valueOf(user.getTokens()));
        tvStars.setText(String.valueOf(user.getStars()));
        tvRegion.setText(user.getRegion() != null ? "📍 " + user.getRegion() : "📍 —");

        int league = LeagueLogic.calculateLeague(user.getStars());
        tvLeague.setText(LeagueLogic.getLeagueIcon(league) + " " + LeagueLogic.getLeagueName(league));

        // Ažuriraj u bazi ako se liga promenila
        if (league != user.getLeague()) {
            userRepo.updateField("league", league, new UserRepository.Callback<Void>() {
                @Override public void onSuccess(Void r) {}
                @Override public void onError(Exception e) {}
            });
        }
    }

    /** Generiše QR kod iz UID-a korisnika i prikazuje ga */
    private void generateQrCode(String uid) {
        try {
            BarcodeEncoder encoder = new BarcodeEncoder();
            Bitmap bitmap = encoder.encodeBitmap(
                    "slagalica://friend/" + uid,   // custom deep-link shema
                    BarcodeFormat.QR_CODE, 300, 300);
            ivQrCode.setImageBitmap(bitmap);
        } catch (WriterException e) {
            // ako ne uspe, ostaje placeholder ikonica
        }
    }

    private void loadStats() {
        statsRepo.getStats(new StatsRepository.Callback<PlayerStats>() {
            @Override public void onSuccess(PlayerStats s) { applyStats(s); }
            @Override public void onError(Exception e) { /* stats ostaju prazne */ }
        });
    }

    private void applyStats(PlayerStats s) {
        // --- Ukupno / pobede ---
        tvTotalGames.setText(String.valueOf(s.totalGames));
        int winPct = s.totalGames > 0 ? (int)(s.wins * 100 / s.totalGames) : 0;
        pbWinRate.setProgress(winPct);
        tvWinRate.setText(winPct + "%");

        // --- Ko zna zna ---
        long kzzTotal = s.kzzCorrect + s.kzzWrong;
        int kzzPct = kzzTotal > 0 ? (int)(s.kzzCorrect * 100 / kzzTotal) : 0;
        pbKoZnaZna.setProgress(kzzPct);
        tvKoZnaZnaStats.setText(s.kzzCorrect + "✓ / " + s.kzzWrong + "✗");
        long kzzAvg = s.kzzRounds > 0 ? s.kzzTotalScore / s.kzzRounds : 0;
        tvKoZnaZnaAvg.setText("Prosek bodova: " + kzzAvg);

        // --- Spojnice ---
        int spojPct = s.spojniceTotal > 0
                ? (int)(s.spojniceCorrect * 100 / s.spojniceTotal) : 0;
        pbSpojnice.setProgress(spojPct);
        tvSpojniceStats.setText(spojPct + "% povezanih");
        long spojAvg = s.spojniceRounds > 0 ? s.spojniceTotalScore / s.spojniceRounds : 0;
        tvSpojniceAvg.setText("Prosek bodova: " + spojAvg);

        // --- Asocijacije ---
        int asoRatio = s.asocijacijeTotal > 0
                ? (int)(s.asocijacijeSolved * 100 / s.asocijacijeTotal) : 0;
        pbAsocijacije.setProgress(asoRatio);
        tvAsocijacijeStats.setText(s.asocijacijeSolved + "✓ / "
                + (s.asocijacijeTotal - s.asocijacijeSolved) + "✗");
        long asoAvg = s.asocijacijeRounds > 0
                ? s.asocijacijeTotalScore / s.asocijacijeRounds : 0;
        tvAsocijacijeAvg.setText("Prosek bodova: " + asoAvg);

        // --- Skočko ---
        int skockoPct = s.skockoTotal > 0
                ? (int)(s.skockoSolved * 100 / s.skockoTotal) : 0;
        pbSkocko.setProgress(skockoPct);
        tvSkockoStats.setText(skockoPct + "% pogođeno");
        long skockoAvg = s.skockoRounds > 0 ? s.skockoTotalScore / s.skockoRounds : 0;
        tvSkockoAvg.setText("Prosek bodova: " + skockoAvg);

        // --- Korak po korak ---
        int korakPct = s.korakTotal > 0
                ? (int)(s.korakSolved * 100 / s.korakTotal) : 0;
        pbKorakPoKorak.setProgress(korakPct);
        tvKorakStats.setText(korakPct + "% pogođeno");
        long korakAvg = s.korakRounds > 0 ? s.korakTotalScore / s.korakRounds : 0;
        tvKorakAvg.setText("Prosek bodova: " + korakAvg);

        // --- Moj broj ---
        int mojBrojPct = s.mojBrojTotal > 0
                ? (int)(s.mojBrojExact * 100 / s.mojBrojTotal) : 0;
        pbMojBroj.setProgress(mojBrojPct);
        tvMojBrojStats.setText(mojBrojPct + "% tačnih");
        long mojBrojAvg = s.mojBrojRounds > 0
                ? s.mojBrojTotalScore / s.mojBrojRounds : 0;
        tvMojBrojAvg.setText("Prosek bodova: " + mojBrojAvg);
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
                        @Override public void onSuccess(Void r) {
                            tvAvatar.setText(chosen);
                            Toast.makeText(ProfileActivity.this,
                                    "Avatar promenjen", Toast.LENGTH_SHORT).show();
                        }
                        @Override public void onError(Exception e) {
                            Toast.makeText(ProfileActivity.this,
                                    "Greška pri promeni avatara", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .show();
    }
    @Override
    protected void onResume() {
        super.onResume();
        loadAll(); // svaki put kad se vrati na profil, ponovo učita podatke
    }
    private void logout() {
        FirebaseAuth.getInstance().signOut();
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }
}