package com.example.slagalica.data.repository;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TournamentRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    public void joinQueue(String userId, Callback<String> cb) {
        // Simple queue: find a tournament with status "waiting" and less than 4 players
        db.collection("tournaments")
                .whereEqualTo("status", "waiting")
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) {
                        // Create new tournament
                        Map<String, Object> t = new HashMap<>();
                        List<String> players = new ArrayList<>();
                        players.add(userId);
                        t.put("players", players);
                        t.put("status", "waiting");
                        db.collection("tournaments").add(t).addOnSuccessListener(ref -> cb.onSuccess(ref.getId()));
                    } else {
                        String id = snap.getDocuments().get(0).getId();
                        db.collection("tournaments").document(id).update("players", com.google.firebase.firestore.FieldValue.arrayUnion(userId))
                                .addOnSuccessListener(v -> cb.onSuccess(id));
                    }
                })
                .addOnFailureListener(cb::onError);
    }

    public ListenerRegistration listenToTournament(String id, Callback<Map<String, Object>> cb) {
        return db.collection("tournaments").document(id).addSnapshotListener((snap, e) -> {
            if (e != null) { cb.onError(e); return; }
            if (snap != null && snap.exists()) cb.onSuccess(snap.getData());
        });
    }

    public void startTournament(String id, List<String> players) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "semifinals");
        // Create matches... simplified for now
        db.collection("tournaments").document(id).update(updates);
    }
}