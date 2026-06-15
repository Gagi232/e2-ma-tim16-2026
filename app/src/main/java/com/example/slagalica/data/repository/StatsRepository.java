package com.example.slagalica.data.repository;

import com.example.slagalica.data.model.PlayerStats;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class StatsRepository {

    private static final String STATS = "stats";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    private String uid() {
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }

    /** Učitaj statistiku trenutnog korisnika */
    public void getStats(Callback<PlayerStats> callback) {
        String uid = uid();
        if (uid == null) { callback.onError(new Exception("Nije ulogovan")); return; }
        db.collection(STATS).document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        PlayerStats s = doc.toObject(PlayerStats.class);
                        callback.onSuccess(s != null ? s : new PlayerStats());
                    } else {
                        callback.onSuccess(new PlayerStats()); // novi igrač, sve 0
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Atomično uvećaj više polja odjednom.
     * Primer: incrementFields(Map.of("kzzCorrect", 2, "kzzWrong", 1, "kzzTotalScore", 15))
     */
    public void incrementFields(Map<String, Long> increments, Callback<Void> callback) {
        String uid = uid();
        if (uid == null) { callback.onError(new Exception("Nije ulogovan")); return; }
        DocumentReference ref = db.collection(STATS).document(uid);
        Map<String, Object> updates = new HashMap<>();
        for (Map.Entry<String, Long> e : increments.entrySet()) {
            updates.put(e.getKey(), FieldValue.increment(e.getValue()));
        }
        ref.set(updates, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(v -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    // ---- Convenience metode za svaku igru ----

    /** Poziva se na kraju Ko zna zna runde */
    public void saveKoZnaZnaResult(int correct, int wrong, int score, Callback<Void> cb) {
        Map<String, Long> inc = new HashMap<>();
        inc.put("kzzCorrect",    (long) correct);
        inc.put("kzzWrong",      (long) wrong);
        inc.put("kzzTotalScore", (long) score);
        inc.put("kzzRounds",     1L);
        incrementFields(inc, cb);
    }

    /** Poziva se na kraju Spojnice runde */
    public void saveSpojniceResult(int connected, int total, int score, Callback<Void> cb) {
        Map<String, Long> inc = new HashMap<>();
        inc.put("spojniceCorrect",     (long) connected);
        inc.put("spojniceTotal",       (long) total);
        inc.put("spojniceTotalScore",  (long) score);
        inc.put("spojniceRounds",      1L);
        incrementFields(inc, cb);
    }

    /** Poziva se na kraju Asocijacije runde */
    public void saveAsocijacijeResult(boolean solved, int score, Callback<Void> cb) {
        Map<String, Long> inc = new HashMap<>();
        inc.put("asocijacijeSolved",    solved ? 1L : 0L);
        inc.put("asocijacijeTotal",     1L);
        inc.put("asocijacijeTotalScore",(long) score);
        inc.put("asocijacijeRounds",    1L);
        incrementFields(inc, cb);
    }

    /** Poziva se na kraju Skočko runde */
    public void saveSkockoResult(boolean solved, int score, Callback<Void> cb) {
        Map<String, Long> inc = new HashMap<>();
        inc.put("skockoSolved",     solved ? 1L : 0L);
        inc.put("skockoTotal",      1L);
        inc.put("skockoTotalScore", (long) score);
        inc.put("skockoRounds",     1L);
        incrementFields(inc, cb);
    }

    /** Poziva se na kraju Korak po korak runde */
    public void saveKorakResult(boolean solved, int score, Callback<Void> cb) {
        Map<String, Long> inc = new HashMap<>();
        inc.put("korakSolved",     solved ? 1L : 0L);
        inc.put("korakTotal",      1L);
        inc.put("korakTotalScore", (long) score);
        inc.put("korakRounds",     1L);
        incrementFields(inc, cb);
    }

    /** Poziva se na kraju Moj broj runde */
    public void saveMojBrojResult(boolean exact, int score, Callback<Void> cb) {
        Map<String, Long> inc = new HashMap<>();
        inc.put("mojBrojExact",      exact ? 1L : 0L);
        inc.put("mojBrojTotal",      1L);
        inc.put("mojBrojTotalScore", (long) score);
        inc.put("mojBrojRounds",     1L);
        incrementFields(inc, cb);
    }

    /** Poziva se na kraju cele partije */
    public void saveMatchResult(boolean won, Callback<Void> cb) {
        Map<String, Long> inc = new HashMap<>();
        inc.put("totalGames", 1L);
        inc.put("wins",       won ? 1L : 0L);
        incrementFields(inc, cb);
    }
}