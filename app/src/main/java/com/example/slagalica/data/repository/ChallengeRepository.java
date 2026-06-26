package com.example.slagalica.data.repository;

import com.example.slagalica.data.model.Challenge;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChallengeRepository {

    private static final int MAX_PARTICIPANTS = 4;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    public void createChallenge(String creatorId, String creatorUsername,
                                String region, int stars, int tokens,
                                Callback<String> cb) {
        Map<String, Object> data = new HashMap<>();
        data.put("creatorId", creatorId);
        data.put("creatorUsername", creatorUsername);
        data.put("region", region);
        data.put("starsWager", stars);
        data.put("tokensWager", tokens);
        data.put("status", "open");
        data.put("createdAt", System.currentTimeMillis());

        // Kreator je automatski prvi učesnik
        Map<String, Object> creatorEntry = new HashMap<>();
        creatorEntry.put("username", creatorUsername);
        creatorEntry.put("score", -1); // -1 = još nije odigrao
        creatorEntry.put("finished", false);
        data.put("participants", new HashMap<String, Object>() {{
            put(creatorId, creatorEntry);
        }});

        db.collection("challenges").add(data)
                .addOnSuccessListener(ref -> cb.onSuccess(ref.getId()))
                .addOnFailureListener(cb::onError);
    }

    public void joinChallenge(String challengeId, String userId, String username,
                              Callback<Void> cb) {
        DocumentReference ref = db.collection("challenges").document(challengeId);
        db.runTransaction(transaction -> {
                    com.google.firebase.firestore.DocumentSnapshot snap = transaction.get(ref);
                    if (!snap.exists()) throw new com.google.firebase.firestore.FirebaseFirestoreException(
                            "Izazov ne postoji", com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND);
                    String status = snap.getString("status");
                    if (!"open".equals(status)) throw new com.google.firebase.firestore.FirebaseFirestoreException(
                            "Izazov više nije dostupan", com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED);

                    Map<String, Object> participants = (Map<String, Object>) snap.get("participants");
                    int count = participants != null ? participants.size() : 0;
                    if (count >= MAX_PARTICIPANTS) throw new com.google.firebase.firestore.FirebaseFirestoreException(
                            "Izazov je pun", com.google.firebase.firestore.FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED);

                    Map<String, Object> entry = new HashMap<>();
                    entry.put("username", username);
                    entry.put("score", -1);
                    entry.put("finished", false);
                    transaction.update(ref, "participants." + userId, entry);

                    if (count + 1 >= MAX_PARTICIPANTS) {
                        transaction.update(ref, "status", "full");
                    }
                    return null;
                }).addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(cb::onError);
    }

    public void submitScore(String challengeId, String userId, int score, Callback<Void> cb) {
        DocumentReference ref = db.collection("challenges").document(challengeId);
        Map<String, Object> updates = new HashMap<>();
        updates.put("participants." + userId + ".score", score);
        updates.put("participants." + userId + ".finished", true);
        ref.update(updates)
                .addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(cb::onError);
    }

    public void getOpenChallengesForRegion(String region, Callback<List<Challenge>> cb) {
        db.collection("challenges")
                .whereEqualTo("region", region)
                .whereEqualTo("status", "open")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .addOnSuccessListener(snap -> {
                    List<Challenge> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        Challenge c = doc.toObject(Challenge.class);
                        c.setId(doc.getId());
                        list.add(c);
                    }
                    cb.onSuccess(list);
                })
                .addOnFailureListener(cb::onError);
    }

    public ListenerRegistration listenToChallenge(String challengeId, Callback<Challenge> cb) {
        return db.collection("challenges").document(challengeId)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) { cb.onError(e); return; }
                    if (snap != null && snap.exists()) {
                        Challenge c = snap.toObject(Challenge.class);
                        c.setId(snap.getId());
                        cb.onSuccess(c);
                    }
                });
    }
}