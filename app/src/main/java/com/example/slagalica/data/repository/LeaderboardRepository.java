package com.example.slagalica.data.repository;

import com.example.slagalica.data.model.LeaderboardEntry;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class LeaderboardRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    public void getWeeklyRankings(Callback<List<LeaderboardEntry>> cb) {
        db.collection("leaderboard_weekly")
                .orderBy("stars", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(snap -> {
                    List<LeaderboardEntry> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        list.add(doc.toObject(LeaderboardEntry.class));
                    }
                    cb.onSuccess(list);
                })
                .addOnFailureListener(cb::onError);
    }

    public void getMonthlyRankings(Callback<List<LeaderboardEntry>> cb) {
        db.collection("leaderboard_monthly")
                .orderBy("stars", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(snap -> {
                    List<LeaderboardEntry> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        list.add(doc.toObject(LeaderboardEntry.class));
                    }
                    cb.onSuccess(list);
                })
                .addOnFailureListener(cb::onError);
    }

    public void getRegionalRankings(Callback<List<LeaderboardEntry>> cb) {
        // This is for monthly by region stars sum
        db.collection("leaderboard_regional")
                .orderBy("stars", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snap -> {
                    List<LeaderboardEntry> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        list.add(doc.toObject(LeaderboardEntry.class));
                    }
                    cb.onSuccess(list);
                })
                .addOnFailureListener(cb::onError);
    }
}