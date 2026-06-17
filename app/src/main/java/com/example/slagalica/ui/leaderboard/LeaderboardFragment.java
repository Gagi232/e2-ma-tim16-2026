package com.example.slagalica.ui.leaderboard;

import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.data.repository.LeaderboardRepository;
import com.google.android.material.button.MaterialButton;

public class LeaderboardFragment extends Fragment {

    private RecyclerView rvLeaderboard;
    private LeaderboardAdapter adapter;
    private final LeaderboardRepository repo = new LeaderboardRepository();
    private boolean showingWeekly = true;

    private TextView tvDateRange;
    private MaterialButton btnWeekly, btnMonthly;

    private final Handler refreshHandler = new Handler();
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            loadData();
            refreshHandler.postDelayed(this, 120_000); // 2 minutes
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_leaderboard, container, false);

        rvLeaderboard = v.findViewById(R.id.rvLeaderboard);
        tvDateRange = v.findViewById(R.id.tvDateRange);
        btnWeekly = v.findViewById(R.id.btnWeekly);
        btnMonthly = v.findViewById(R.id.btnMonthly);

        adapter = new LeaderboardAdapter();
        rvLeaderboard.setAdapter(adapter);

        btnWeekly.setOnClickListener(view -> {
            showingWeekly = true;
            loadData();
        });

        btnMonthly.setOnClickListener(view -> {
            showingWeekly = false;
            loadData();
        });

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshHandler.post(refreshRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void loadData() {
        if (showingWeekly) {
            repo.getWeeklyRankings(new LeaderboardRepository.Callback<java.util.List<com.example.slagalica.data.model.LeaderboardEntry>>() {
                @Override public void onSuccess(java.util.List<com.example.slagalica.data.model.LeaderboardEntry> result) {
                    adapter.setEntries(result);
                    tvDateRange.setText("Tekuća nedelja");
                }
                @Override public void onError(Exception e) {}
            });
        } else {
            repo.getMonthlyRankings(new LeaderboardRepository.Callback<java.util.List<com.example.slagalica.data.model.LeaderboardEntry>>() {
                @Override public void onSuccess(java.util.List<com.example.slagalica.data.model.LeaderboardEntry> result) {
                    adapter.setEntries(result);
                    tvDateRange.setText("Tekući mesec");
                }
                @Override public void onError(Exception e) {}
            });
        }
    }
}