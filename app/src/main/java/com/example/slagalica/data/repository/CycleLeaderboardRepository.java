package com.example.slagalica.data.repository;

import com.example.slagalica.data.model.CycleEntry;
import com.google.firebase.firestore.*;
import com.example.slagalica.data.model.AppNotification;
import com.example.slagalica.logic.AppNotificationManager;
import java.util.*;

public class CycleLeaderboardRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    private static final int[] WEEKLY_TOKEN_REWARDS  = {5, 3, 2, 1, 1, 1, 1, 1, 1, 1}; // mesto 1-10
    private static final int[] MONTHLY_TOKEN_REWARDS = {10, 6, 4, 2, 2, 2, 2, 2, 2, 2};

    public void checkAndRunWeeklyReset(Callback<Void> cb) {
        checkAndRunReset("cycle_meta", "weekly", weekKey(), "cycle_stars_weekly", WEEKLY_TOKEN_REWARDS, "nedeljnu", cb);
    }

    public void checkAndRunMonthlyReset(Callback<Void> cb) {
        checkAndRunReset("cycle_meta", "monthly", monthKey(), "cycle_stars_monthly", MONTHLY_TOKEN_REWARDS, "mesečnu", cb);
    }

    private void checkAndRunReset(String metaCollection, String metaDocId, String nowKey,
                                  String statsCollection, int[] rewards, String cycleLabel, Callback<Void> cb) {
        DocumentReference metaRef = db.collection(metaCollection).document(metaDocId);
        metaRef.get().addOnSuccessListener(metaSnap -> {
            String storedKey = metaSnap.exists() ? metaSnap.getString("cycleKey") : null;
            if (nowKey.equals(storedKey)) {
                cb.onSuccess(null); // ciklus nije istekao
                return;
            }
            distributeRewardsAndReset(metaRef, nowKey, storedKey, statsCollection, rewards, cycleLabel, cb);
        }).addOnFailureListener(cb::onError);
    }

    private void distributeRewardsAndReset(DocumentReference metaRef, String nowKey, String prevKey,
                                           String statsCollection, int[] rewards, String cycleLabel, Callback<Void> cb) {
        if (prevKey == null) {
            metaRef.set(Collections.singletonMap("cycleKey", nowKey))
                    .addOnSuccessListener(v -> cb.onSuccess(null))
                    .addOnFailureListener(cb::onError);
            return;
        }

        db.collection(statsCollection)
                .whereEqualTo("cycleKey", prevKey)
                .orderBy("stars", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .addOnSuccessListener(snap -> {
                    List<DocumentSnapshot> docs = snap.getDocuments();

                    db.runTransaction(transaction -> {
                        DocumentSnapshot checkSnap = transaction.get(metaRef);
                        String checkKey = checkSnap.exists() ? checkSnap.getString("cycleKey") : null;
                        if (nowKey.equals(checkKey)) return null;
                        transaction.set(metaRef, Collections.singletonMap("cycleKey", nowKey));
                        return null;
                    }).addOnSuccessListener(v -> {
                        grantRewards(docs, rewards, cycleLabel, prevKey);
                        cb.onSuccess(null);
                    }).addOnFailureListener(cb::onError);
                })
                .addOnFailureListener(cb::onError);
    }

    private void grantRewards(List<DocumentSnapshot> rankedDocs, int[] rewards, String cycleLabel, String prevKey) {
        for (int i = 0; i < rankedDocs.size() && i < rewards.length; i++) {
            DocumentSnapshot doc = rankedDocs.get(i);
            String userId = doc.getId();
            String username = doc.getString("username");
            int place = i + 1;
            int tokenReward = rewards[i];

            db.collection("users").document(userId)
                    .update("tokens", FieldValue.increment(tokenReward));

            Map<String, Object> pending = new HashMap<>();
            pending.put("place", place);
            pending.put("tokens", tokenReward);
            pending.put("cycleLabel", cycleLabel);
            pending.put("shown", false);
            pending.put("createdAt", System.currentTimeMillis());
            db.collection("pending_rewards").add(pending).addOnSuccessListener(ref -> {
                ref.update("userId", userId);
            });

            AppNotification notif = new AppNotification();
            notif.setUserId(userId);
            notif.setType(AppNotificationManager.TYPE_REWARD);
            notif.setMessage("Čestitamo " + username + "! Osvojili ste " + place + ". mesto na "
                    + cycleLabel + " rang listi i dobili " + tokenReward + " tokena! 🎉");
            notif.setRead(false);
            notif.setCreatedAt(System.currentTimeMillis());
            db.collection("notifications").add(notif);
        }
    }

    // ---- Ključevi ciklusa (bez java.time, samo Calendar) ----

    public String weekKey() {
        Calendar c = Calendar.getInstance();
        c.setMinimalDaysInFirstWeek(4); // ISO-slično ponašanje
        c.setFirstDayOfWeek(Calendar.MONDAY);
        int week = c.get(Calendar.WEEK_OF_YEAR);
        int year = c.get(Calendar.YEAR);
        return year + "-W" + (week < 10 ? "0" + week : week);
    }

    public String monthKey() {
        Calendar c = Calendar.getInstance();
        int month = c.get(Calendar.MONTH) + 1;
        return c.get(Calendar.YEAR) + "-" + (month < 10 ? "0" + month : month);
    }

    /** Opseg datuma za prikaz (zahtev 4e) - početak i kraj TEKUĆE nedelje/meseca. */
    public String currentWeekRangeLabel() {
        Calendar c = Calendar.getInstance();
        c.setFirstDayOfWeek(Calendar.MONDAY);
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("dd.MM.", Locale.getDefault());
        String start = fmt.format(c.getTime());
        c.add(Calendar.DAY_OF_WEEK, 6);
        String end = fmt.format(c.getTime());
        return start + " - " + end;
    }

    public String currentMonthRangeLabel() {
        Calendar c = Calendar.getInstance();
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("MMMM yyyy", new Locale("sr"));
        return fmt.format(c.getTime());
    }

    // ---- Upis (poziva se nakon SVAKE partije, pobeda ili gubitak) ----

    /**
     * Zahtev 4a: "odigra bar jednu partiju u ciklusu" - zato OVO zovemo uvek kad partija završi,
     * bez obzira na starsChange predznak. Dokument se kreira samim pozivom (igrač postaje "rangiran").
     */
    public void recordMatch(String userId, String username, int league, int starsChange) {
        recordForCollection("cycle_stars_weekly", userId, username, league, starsChange, weekKey());
        recordForCollection("cycle_stars_monthly", userId, username, league, starsChange, monthKey());
    }

    private void recordForCollection(String collection, String userId, String username,
                                     int league, int starsChange, String currentCycleKey) {
        DocumentReference ref = db.collection(collection).document(userId);
        db.runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(ref);
            long currentStars = 0;
            String storedKey = snap.exists() ? snap.getString("cycleKey") : null;
            if (snap.exists() && currentCycleKey.equals(storedKey) && snap.contains("stars")) {
                currentStars = snap.getLong("stars");
            }
            // Ako je storedKey razlicit (nov ciklus a reset jos nije stigao) - krece od 0, kao da je nov igrac
            Map<String, Object> data = new HashMap<>();
            data.put("stars", currentStars + starsChange);
            data.put("cycleKey", currentCycleKey);
            data.put("username", username);
            data.put("league", league);
            transaction.set(ref, data, SetOptions.merge());
            return null;
        });
    }

    // ---- Čitanje za prikaz (zahtev 4b) ----

    public void getWeeklyRanking(Callback<List<CycleEntry>> cb) {
        getRanking("cycle_stars_weekly", "cycleKey", weekKey(), cb);
    }

    public void getMonthlyRanking(Callback<List<CycleEntry>> cb) {
        getRanking("cycle_stars_monthly", "cycleKey", monthKey(), cb);
    }

    private void getRanking(String collection, String keyField, String currentKey, Callback<List<CycleEntry>> cb) {
        db.collection(collection)
                .whereEqualTo(keyField, currentKey)
                .orderBy("stars", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(snap -> {
                    List<CycleEntry> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        CycleEntry e = doc.toObject(CycleEntry.class);
                        e.setUserId(doc.getId());
                        list.add(e);
                    }
                    cb.onSuccess(list);
                })
                .addOnFailureListener(cb::onError);
    }
}