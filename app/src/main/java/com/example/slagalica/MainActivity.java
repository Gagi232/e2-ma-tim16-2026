package com.example.slagalica;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
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
}