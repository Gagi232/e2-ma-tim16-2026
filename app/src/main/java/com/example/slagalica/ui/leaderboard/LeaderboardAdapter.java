package com.example.slagalica.ui.leaderboard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.data.model.LeaderboardEntry;
import com.example.slagalica.logic.LeagueLogic;

import java.util.ArrayList;
import java.util.List;

public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.ViewHolder> {

    private List<LeaderboardEntry> entries = new ArrayList<>();

    public void setEntries(List<LeaderboardEntry> entries) {
        this.entries = entries;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_leaderboard, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LeaderboardEntry entry = entries.get(position);
        holder.tvRank.setText(String.valueOf(position + 1));
        holder.tvUsername.setText(entry.getUsername());
        holder.tvStars.setText(entry.getStars() + " ⭐");
        holder.tvLeagueIcon.setText(LeagueLogic.getLeagueIcon(entry.getLeague()));
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvRank, tvUsername, tvStars, tvLeagueIcon;
        ViewHolder(View v) {
            super(v);
            tvRank = v.findViewById(R.id.tvRank);
            tvUsername = v.findViewById(R.id.tvUsername);
            tvStars = v.findViewById(R.id.tvStars);
            tvLeagueIcon = v.findViewById(R.id.tvLeagueIcon);
        }
    }
}