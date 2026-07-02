package com.example.slagalica;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.slagalica.data.remote.DatabaseSeeder;
import com.example.slagalica.data.repository.MatchmakingRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.logic.LeagueLogic;
import com.example.slagalica.ui.friends.FriendsFragment;
import com.example.slagalica.ui.games.KoZnaZnaActivity;
import com.example.slagalica.ui.leaderboard.LeaderboardFragment;
import com.example.slagalica.ui.main.HomeFragment;
import com.example.slagalica.ui.notification.NotificationsFragment;
import com.example.slagalica.ui.profile.ProfileActivity;
import com.example.slagalica.ui.region.RegionsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class MainActivity extends AppCompatActivity {

    private com.google.firebase.firestore.ListenerRegistration notifListenerReg;
    private java.util.Set<String> seenNotifIds = new java.util.HashSet<>();
    private TextView tvTokens, tvStars, tvLeague, tvProfile;

    private final MatchmakingRepository matchmakingRepo = new MatchmakingRepository();
    private AlertDialog searchingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvLeague = findViewById(R.id.tvLeague);
        tvStars  = findViewById(R.id.tvStars);
        tvTokens = findViewById(R.id.tvTokens);
        tvProfile = findViewById(R.id.tvProfile);

        DatabaseSeeder.seedAll();
        tvLeague.setOnClickListener(v -> showLeagueDialog());
        MaterialButton btnPlay = findViewById(R.id.btnPlay);

        btnPlay.setOnClickListener(v -> startRandomMatch());

        new UserRepository().setOnline(true, null);
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);

        updateTopBar();

        new UserRepository().checkAndGrantDailyTokens(new UserRepository.Callback<Void>() {
            @Override public void onSuccess(Void r) { updateTopBar(); }
            @Override public void onError(Exception e) {}
        });

        new com.example.slagalica.data.repository.RegionStatsRepository()
                .checkAndRunMonthlyReset(new com.example.slagalica.data.repository.RegionStatsRepository.Callback<Void>() {
                    @Override public void onSuccess(Void r) {}
                    @Override public void onError(Exception e) {}
                });

        checkPendingRewardDialog();

        com.example.slagalica.data.repository.CycleLeaderboardRepository cycleRepo =
                new com.example.slagalica.data.repository.CycleLeaderboardRepository();

        cycleRepo.checkAndRunWeeklyReset(new com.example.slagalica.data.repository.CycleLeaderboardRepository.Callback<Void>() {
            @Override public void onSuccess(Void r) {}
            @Override public void onError(Exception e) {}
        });
        cycleRepo.checkAndRunMonthlyReset(new com.example.slagalica.data.repository.CycleLeaderboardRepository.Callback<Void>() {
            @Override public void onSuccess(Void r) { checkPendingRewardDialog(); }
            @Override public void onError(Exception e) { checkPendingRewardDialog(); }
        });

        loadFragment(new HomeFragment());

        tvProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class))
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        startGlobalNotificationListener();
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                loadFragment(new HomeFragment());
            } else if (id == R.id.nav_leaderboard) {
                loadFragment(new LeaderboardFragment());
            } else if (id == R.id.nav_friends) {
                loadFragment(new FriendsFragment());
            } else if (id == R.id.nav_regions) {
                loadFragment(new RegionsFragment());
            } else if (id == R.id.nav_notifications) {
                loadFragment(new NotificationsFragment());
            }
            return true;
        });
    }
    @Override
    protected void onResume() {
        super.onResume();
        new UserRepository().setOnline(true, null);
        updateTopBar();
    }
    private void startRandomMatch() {
        String myId = FirebaseAuth.getInstance().getUid();
        if (myId == null) return;

        new com.example.slagalica.data.repository.UserRepository().spendToken(
                new com.example.slagalica.data.repository.UserRepository.Callback<Void>() {
                    @Override
                    public void onSuccess(Void r) {
                        showSearchingDialog();
                        matchmakingRepo.findMatch(myId, (matchId, opponentId, isPlayer1) -> {
                            if (searchingDialog != null) searchingDialog.dismiss();
                            Intent intent = new Intent(MainActivity.this, KoZnaZnaActivity.class);
                            intent.putExtra("isGuest", false);
                            intent.putExtra("matchId", matchId);
                            intent.putExtra("myId", myId);
                            intent.putExtra("opponentId", opponentId);
                            intent.putExtra("isPlayer1", isPlayer1);
                            intent.putExtra("isFriendly", false);
                            startActivity(intent);
                        }, errorMsg -> {
                            if (searchingDialog != null) searchingDialog.dismiss();
                            Toast.makeText(MainActivity.this, "Greška: " + errorMsg, Toast.LENGTH_SHORT).show();
                        });
                    }
                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showSearchingDialog() {
        searchingDialog = new AlertDialog.Builder(this)
                .setTitle("Traženje protivnika")
                .setMessage("Tražimo ti protivnika...")
                .setNegativeButton("Otkaži", (d, w) -> matchmakingRepo.cancelSearch())
                .setCancelable(false)
                .show();
    }



    private void updateTopBar() {
        UserRepository userRepo = new UserRepository();
        userRepo.getCurrentUser(new UserRepository.Callback<com.example.slagalica.data.model.User>() {
            @Override
            public void onSuccess(com.example.slagalica.data.model.User user) {
                if (user != null) {
                    tvTokens.setText(String.valueOf(user.getTokens()));
                    tvStars.setText(String.valueOf(user.getStars()));

                    int league = LeagueLogic.calculateLeague(user.getStars());
                    tvLeague.setText(LeagueLogic.getLeagueIcon(league));

                    String avatar = user.getAvatarUrl();
                    tvProfile.setText(avatar != null && !avatar.isEmpty() ? avatar : "👤");

                    // Sinkronizacija lige ako se promenila na osnovu zvezda
                    if (league != user.getLeague()) {
                        userRepo.updateField("league", league, new UserRepository.Callback<Void>() {
                            @Override public void onSuccess(Void r) {}
                            @Override public void onError(Exception e) {}
                        });
                    }
                }
            }
            @Override public void onError(Exception e) {}
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        new UserRepository().setOnline(false, null);
    }
    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    private void checkPendingRewardDialog() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        FirebaseFirestore.getInstance()
                .collection("pending_rewards")
                .whereEqualTo("userId", uid)
                .whereEqualTo("shown", false)
                .get()
                .addOnSuccessListener(snap -> {
                    for (QueryDocumentSnapshot doc : snap) {
                        runOnUiThread(() -> showRewardDialog(doc));
                    }
                });
    }

    private void showRewardDialog(com.google.firebase.firestore.QueryDocumentSnapshot doc) {
        long place = doc.getLong("place");
        long tokens = doc.getLong("tokens");
        String cycleLabel = doc.getString("cycleLabel");

        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_reward, null);
        TextView tvRewardText = dialogView.findViewById(R.id.tvRewardText);
        TextView  ivConfetti = dialogView.findViewById(R.id.ivConfetti); // emoji/ikonica za animaciju

        String medal = place == 1 ? "🥇" : place == 2 ? "🥈" : place == 3 ? "🥉" : "🏅";
        tvRewardText.setText(medal + " " + place + ". mesto na " + cycleLabel + " rang listi!\n+" + tokens + " tokena");

        // Jednostavna scale animacija
        android.view.animation.ScaleAnimation scaleAnim = new android.view.animation.ScaleAnimation(
                0.3f, 1f, 0.3f, 1f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f);
        scaleAnim.setDuration(500);
        scaleAnim.setInterpolator(new android.view.animation.OvershootInterpolator());
        ivConfetti.startAnimation(scaleAnim);

        // Zvuk - kratak sistemski ton
        android.media.RingtoneManager.getRingtone(this,
                android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)).play();

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Super!", (d, w) -> {
                    doc.getReference().update("shown", true);
                })
                .setCancelable(false)
                .show();
    }
    private void startGlobalNotificationListener() {
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        notifListenerReg = new com.example.slagalica.data.repository.NotificationRepository()
                .listenForUser(uid, list -> {
                    for (com.example.slagalica.data.model.AppNotification n : list) {
                        if (!n.isRead() && !seenNotifIds.contains(n.getId())) {
                            seenNotifIds.add(n.getId());
                            showSystemNotification(n);
                        }
                    }
                }, e -> {});
    }

    private void showSystemNotification(com.example.slagalica.data.model.AppNotification n) {
        String channelId = "game_invites";
        android.app.NotificationManager manager =
                (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    channelId, "Pozivi i obaveštenja", android.app.NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return; // nema dozvole, ne pucaj
        }

        androidx.core.app.NotificationCompat.Builder builder =
                new androidx.core.app.NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.mipmap.ic_launcher) // zameni svojom ikonicom ako imaš bolju
                        .setContentTitle("Slagalica")
                        .setContentText(n.getMessage())
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true);

        manager.notify(n.getId().hashCode(), builder.build());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (notifListenerReg != null) notifListenerReg.remove();
        if (searchingDialog != null && searchingDialog.isShowing()) {
            matchmakingRepo.cancelSearch();
            searchingDialog.dismiss();
        }
    }

    private void showLeagueDialog() {

        String[] leagues ={
                "🏆 Liga 0",
                "📚 Početnička Liga",
                "🧠 Školska Liga",
                "🏛️ Akademska Liga",
                "👑 Genijalac Liga"
        };

        new AlertDialog.Builder(this)
                .setTitle("Lige")
                .setItems(leagues, (dialog, which) -> {

                    Toast.makeText(this,
                            "Lige: " + leagues[which],
                            Toast.LENGTH_SHORT).show();

                })
                .show();
    }

}