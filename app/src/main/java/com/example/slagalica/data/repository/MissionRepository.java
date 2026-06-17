package com.example.slagalica.data.repository;

import com.example.slagalica.data.model.DailyMission;
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
}