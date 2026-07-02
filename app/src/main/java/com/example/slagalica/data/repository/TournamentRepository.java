package com.example.slagalica.data.repository;

import com.google.firebase.firestore.*;
import java.util.*;

public class TournamentRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    /** Ulazak u red čekanja. Vraća tournamentId. */
    public void joinQueue(String userId, Map<String, Object> playerData, Callback<String> cb) {
        db.collection("tournaments")
                .whereEqualTo("status", "waiting")
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) {
                        // Napravi novi turnir
                        Map<String, Object> t = new HashMap<>();
                        t.put("players", new ArrayList<>(Collections.singletonList(userId)));
                        t.put("playerData", new HashMap<String, Object>() {{ put(userId, playerData); }});
                        t.put("status", "waiting");
                        t.put("createdAt", System.currentTimeMillis());
                        db.collection("tournaments").add(t)
                                .addOnSuccessListener(ref -> cb.onSuccess(ref.getId()))
                                .addOnFailureListener(cb::onError);
                    } else {
                        String id = snap.getDocuments().get(0).getId();
                        DocumentReference ref = db.collection("tournaments").document(id);
                        db.runTransaction(transaction -> {
                                    DocumentSnapshot tSnap = transaction.get(ref);
                                    List<String> players = (List<String>) tSnap.get("players");
                                    if (players == null) players = new ArrayList<>();
                                    if (players.contains(userId)) return id;
                                    players.add(userId);
                                    transaction.update(ref, "players", players);
                                    transaction.update(ref, "playerData." + userId, playerData);
                                    if (players.size() == 4) {
                                        // Formiraj mečeve
                                        Collections.shuffle(players);
                                        Map<String, Object> sf1 = new HashMap<>();
                                        sf1.put("player1", players.get(0));
                                        sf1.put("player2", players.get(1));
                                        sf1.put("winnerId", null);
                                        Map<String, Object> sf2 = new HashMap<>();
                                        sf2.put("player1", players.get(2));
                                        sf2.put("player2", players.get(3));
                                        sf2.put("winnerId", null);
                                        transaction.update(ref, "semifinal1", sf1);
                                        transaction.update(ref, "semifinal2", sf2);
                                        transaction.update(ref, "status", "semifinals");
                                    }
                                    return id;
                                }).addOnSuccessListener(result -> cb.onSuccess(id))
                                .addOnFailureListener(cb::onError);
                    }
                })
                .addOnFailureListener(cb::onError);
    }

    /** Napusti red čekanja (samo ako je status još "waiting") */
    public void leaveQueue(String tournamentId, String userId) {
        DocumentReference ref = db.collection("tournaments").document(tournamentId);
        ref.get().addOnSuccessListener(snap -> {
            if ("waiting".equals(snap.getString("status"))) {
                ref.update("players", FieldValue.arrayRemove(userId));
            }
        });
    }

    /** Upiši pobednika polufinala */
    public void submitSemifinalResult(String tournamentId, String matchKey,
                                      String winnerId, Callback<Void> cb) {
        if ("final".equals(matchKey)) {
            submitFinalResult(tournamentId, winnerId, cb);
            return;
        }
        DocumentReference ref = db.collection("tournaments").document(tournamentId);
        db.runTransaction(transaction -> {
                    DocumentSnapshot snap = transaction.get(ref);
                    transaction.update(ref, matchKey + ".winnerId", winnerId);

                    // Provjeri da li su oba polufinala gotova
                    String otherKey = "semifinal1".equals(matchKey) ? "semifinal2" : "semifinal1";
                    Map<String, Object> otherMatch = (Map<String, Object>) snap.get(otherKey);
                    String otherWinner = otherMatch != null ? (String) otherMatch.get("winnerId") : null;

                    if (otherWinner != null) {
                        // Oba gotova — formiraj finale
                        String w1 = "semifinal1".equals(matchKey) ? winnerId : otherWinner;
                        String w2 = "semifinal1".equals(matchKey) ? otherWinner : winnerId;
                        Map<String, Object> finalMatch = new HashMap<>();
                        finalMatch.put("player1", w1);
                        finalMatch.put("player2", w2);
                        finalMatch.put("winnerId", null);
                        transaction.update(ref, "final", finalMatch);
                        transaction.update(ref, "status", "finals");
                    }
                    return null;
                }).addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(cb::onError);
    }

    /** Upiši pobednika finala */
    public void submitFinalResult(String tournamentId, String winnerId, Callback<Void> cb) {
        db.collection("tournaments").document(tournamentId)
                .update("final.winnerId", winnerId, "status", "finished")
                .addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(cb::onError);
    }

    public ListenerRegistration listenToTournament(String id, Callback<DocumentSnapshot> cb) {
        return db.collection("tournaments").document(id)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) { cb.onError(e); return; }
                    if (snap != null && snap.exists()) cb.onSuccess(snap);
                });
    }
}