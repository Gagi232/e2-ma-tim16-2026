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
import com.example.slagalica.ui.friends.FriendsFragment;
import com.example.slagalica.ui.games.KoZnaZnaActivity;
import com.example.slagalica.ui.leaderboard.LeaderboardFragment;
import com.example.slagalica.ui.main.HomeFragment;
import com.example.slagalica.ui.notification.NotificationsFragment;
import com.example.slagalica.ui.profile.ProfileActivity;
import com.example.slagalica.ui.region.RegionsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TextView tvLeague = findViewById(R.id.tvLeague);
        DatabaseSeeder.seedAll();
        tvLeague.setOnClickListener(v -> showLeagueDialog());
        ImageView ivProfile = findViewById(R.id.ivProfile);
        MaterialButton btnPlay = findViewById(R.id.btnPlay);
        btnPlay.setOnClickListener(v ->
                startActivity(new Intent(this, KoZnaZnaActivity.class))
        );
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);

        loadFragment(new HomeFragment());

        ivProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class))
        );

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

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
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