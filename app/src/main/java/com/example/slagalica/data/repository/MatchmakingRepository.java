package com.example.slagalica.data.repository;

import com.google.firebase.database.*;

import java.util.HashMap;
import java.util.Map;

public class MatchmakingRepository {

    private final DatabaseReference queueRef = FirebaseDatabase.getInstance().getReference("matchmaking_queue");
    private final DatabaseReference matchesRef = FirebaseDatabase.getInstance().getReference("activeMatches");

    public interface OnMatchFound {
        void onFound(String matchId, String opponentId, boolean isPlayer1);
    }
    public interface OnError { void onError(String message); }

    private ValueEventListener queueListener;
    private String myQueueKey;
    private boolean matchAlreadyFound = false;

    public void findMatch(String myId, OnMatchFound onFound, OnError onError) {
        matchAlreadyFound = false;
        myQueueKey = queueRef.push().getKey();
        if (myQueueKey == null) { onError.onError("Ne mogu da generišem queue key"); return; }

        Map<String, Object> entry = new HashMap<>();
        entry.put("uid", myId);
        entry.put("joinedAt", ServerValue.TIMESTAMP);

        queueRef.child(myQueueKey).setValue(entry).addOnSuccessListener(v ->
                listenToQueue(myId, onFound, onError)
        ).addOnFailureListener(e -> onError.onError(e.getMessage()));
    }

    /** Svi koji čekaju slušaju CEO red - ne samo svoj unos. */
    private void listenToQueue(String myId, OnMatchFound onFound, OnError onError) {
        queueListener = queueRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                if (matchAlreadyFound) return;

                DataSnapshot mine = snap.child(myQueueKey);
                if (!mine.exists()) return;

                // NOVO: citamo matchId direktno sa sopstvenog unosa - nema vise query-a ka /activeMatches
                String assignedMatchId = mine.child("matchId").getValue(String.class);
                String claimedBy = mine.child("claimedBy").getValue(String.class);
                if (assignedMatchId != null && claimedBy != null) {
                    matchAlreadyFound = true;
                    stopWaiting();
                    queueRef.child(myQueueKey).removeValue();
                    onFound.onFound(assignedMatchId, claimedBy, true); // ja sam player1
                    return;
                }

                for (DataSnapshot child : snap.getChildren()) {
                    String key = child.getKey();
                    if (key == null || key.compareTo(myQueueKey) >= 0) continue;
                    String otherUid = child.child("uid").getValue(String.class);
                    if (otherUid == null || otherUid.equals(myId)) continue;
                    if (child.child("claimedBy").exists()) continue;

                    tryPairWith(key, otherUid, myId, onFound, onError);
                    return;
                }
            }
            @Override public void onCancelled(DatabaseError error) { onError.onError(error.getMessage()); }
        });
    }

    private void tryPairWith(String otherQueueKey, String opponentId, String myId, OnMatchFound onFound, OnError onError) {
        queueRef.child(otherQueueKey).child("claimedBy").runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData currentData) {
                if (currentData.getValue() != null) return Transaction.abort();
                currentData.setValue(myId);
                return Transaction.success(currentData);
            }
            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snap) {
                if (error != null) { onError.onError(error.getMessage()); return; }
                if (!committed) return;
                if (matchAlreadyFound) return;
                matchAlreadyFound = true;
                stopWaiting();
                createMatch(myId, opponentId, otherQueueKey, onFound, onError);
            }
        });
    }

    private void createMatch(String myId, String opponentId, String opponentQueueKey, OnMatchFound onFound, OnError onError) {
        String matchId = matchesRef.push().getKey();
        if (matchId == null) { onError.onError("Ne mogu da generišem matchId"); return; }

        Map<String, Object> info = new HashMap<>();
        info.put("player1", opponentId);
        info.put("player2", myId);
        info.put("status", "active");
        info.put("isFriendly", false);

        matchesRef.child(matchId).child("info").setValue(info)
                .addOnSuccessListener(v -> {
                    // NOVO: upisi matchId DIREKTNO na queue unos protivnika - on ce to procitati sam
                    queueRef.child(opponentQueueKey).child("matchId").setValue(matchId)
                            .addOnFailureListener(e -> onError.onError("Ne mogu da javim protivniku: " + e.getMessage()));
                    if (myQueueKey != null) queueRef.child(myQueueKey).removeValue();
                    onFound.onFound(matchId, opponentId, false); // ja sam player2
                })
                .addOnFailureListener(e -> onError.onError(e.getMessage()));
    }

    public void cancelSearch() {
        stopWaiting();
        if (myQueueKey != null) {
            queueRef.child(myQueueKey).removeValue();
            myQueueKey = null;
        }
    }

    private void stopWaiting() {
        if (queueListener != null) queueRef.removeEventListener(queueListener);
        queueListener = null;
    }
}