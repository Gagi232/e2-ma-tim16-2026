package com.example.slagalica.data.repository;

import com.example.slagalica.data.model.DailyMission;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.Calendar;

public class MissionRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    public void getMissions(String userId, Callback<DailyMission> cb) {
        long today = getTodayTimestamp();
        db.collection("missions")
                .whereEqualTo("userId", userId)
                .whereEqualTo("date", today)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) {
                        DailyMission m = new DailyMission();
                        m.setUserId(userId);
                        m.setDate(today);
                        db.collection("missions").add(m).addOnSuccessListener(ref -> {
                            m.setId(ref.getId());
                            cb.onSuccess(m);
                        });
                    } else {
                        DailyMission m = snap.getDocuments().get(0).toObject(DailyMission.class);
                        m.setId(snap.getDocuments().get(0).getId());
                        cb.onSuccess(m);
                    }
                })
                .addOnFailureListener(cb::onError);
    }

    public void updateMission(DailyMission m) {
        db.collection("missions").document(m.getId()).set(m);
    }

    private long getTodayTimestamp() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /**
     * Centralna metoda za otkacivanje misije.
     * missionField: "winMatch", "sendMessage", "friendlyMatch", "winTournament"
     */
    public void completeMission(String userId, String missionField, Callback<Void> cb) {
        getMissions(userId, new Callback<DailyMission>() {
            @Override
            public void onSuccess(DailyMission m) {
                boolean already;
                switch (missionField) {
                    case "winMatch":
                        already = m.isWinMatch();
                        m.setWinMatch(true);
                        break;
                    case "sendMessage":
                        already = m.isSendMessage();
                        m.setSendMessage(true);
                        break;
                    case "friendlyMatch":
                        already = m.isFriendlyMatch();
                        m.setFriendlyMatch(true);
                        break;
                    case "winTournament":
                        already = m.isWinTournament();
                        m.setWinTournament(true);
                        break;
                    default:
                        cb.onError(new Exception("Nepoznata misija: " + missionField));
                        return;
                }

                if (already) {
                    cb.onSuccess(null); // vec uradjena danas, ne dupliraj nagradu
                    return;
                }

                updateMission(m);

                // Nagrada za pojedinacnu misiju - 3 zvezde
                db.collection("users").document(userId)
                        .update("stars", FieldValue.increment(3));

                // Ako su SVE 4 gotove i bonus jos nije dat - dodaj 2 tokena + 3 zvezde
                if (m.allComplete() && !m.isBonusClaimed()) {
                    m.setBonusClaimed(true);
                    updateMission(m);
                    db.collection("users").document(userId)
                            .update("stars", FieldValue.increment(3),
                                    "tokens", FieldValue.increment(2));
                }

                cb.onSuccess(null);
            }
            @Override public void onError(Exception e) { cb.onError(e); }
        });
    }
}