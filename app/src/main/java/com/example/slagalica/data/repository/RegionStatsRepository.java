package com.example.slagalica.data.repository;

import com.example.slagalica.util.RegionUtil;
import com.google.firebase.firestore.*;
import com.google.android.gms.tasks.Tasks;

import java.text.SimpleDateFormat;
import java.util.*;

public class RegionStatsRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    private String currentMonthKey() {
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
    }

    /** Dodaje zvezde regionu u TEKUĆEM ciklusu. Pozvati kad korisnik osvoji zvezde (kraj partije). */
    public void addStarsToRegion(String region, int stars, Callback<Void> cb) {
        if (region == null || stars <= 0) { cb.onSuccess(null); return; }
        DocumentReference ref = db.collection("region_stats").document(region);
        db.runTransaction(transaction -> {
                    DocumentSnapshot snap = transaction.get(ref);
                    long current = snap.exists() && snap.contains("monthlyStars")
                            ? snap.getLong("monthlyStars") : 0L;
                    Map<String, Object> data = new HashMap<>();
                    data.put("monthlyStars", current + stars);
                    data.put("cycleMonthKey", currentMonthKey());
                    transaction.set(ref, data, SetOptions.merge());
                    return null;
                }).addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(cb::onError);
    }

    /**
     * Provera/izvršenje mesečnog reseta. Pozvati jednom pri pokretanju app-a (npr. MainActivity.onCreate).
     * Transakcija na cycle_meta/current garantuje da samo PRVI klijent koji primeti promenu meseca
     * zapravo izvrši reset - svi ostali klijenti koji eventualno uđu u trku će u transakciji
     * vidi već ažuriran monthKey i preskoče posao.
     */
    public void checkAndRunMonthlyReset(Callback<Void> cb) {
        String nowKey = currentMonthKey();
        DocumentReference metaRef = db.collection("cycle_meta").document("current");

        metaRef.get().addOnSuccessListener(metaSnap -> {
            String storedKey = metaSnap.exists() ? metaSnap.getString("monthKey") : null;
            if (nowKey.equals(storedKey)) {
                cb.onSuccess(null); // nema isteka ciklusa, ništa se ne radi
                return;
            }
            runReset(nowKey, storedKey, cb);
        }).addOnFailureListener(cb::onError);
    }

    private void runReset(String nowKey, String prevKey, Callback<Void> cb) {
        db.collection("region_stats").get().addOnSuccessListener(snap -> {
            // Nađi top 3 regiona po monthlyStars iz PROŠLOG ciklusa
            List<Map.Entry<String, Long>> ranked = new ArrayList<>();
            for (QueryDocumentSnapshot doc : snap) {
                long stars = doc.contains("monthlyStars") ? doc.getLong("monthlyStars") : 0L;
                ranked.add(new AbstractMap.SimpleEntry<>(doc.getId(), stars));
            }
            ranked.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

            WriteBatch batch = db.batch();

            // Snimi istoriju samo ako je prošli ciklus postojao (prvi put nema šta da se snimi)
            if (prevKey != null && !ranked.isEmpty()) {
                Map<String, Object> history = new HashMap<>();
                if (ranked.size() > 0) history.put("gold", ranked.get(0).getKey());
                if (ranked.size() > 1) history.put("silver", ranked.get(1).getKey());
                if (ranked.size() > 2) history.put("bronze", ranked.get(2).getKey());
                batch.set(db.collection("region_history").document(prevKey), history);
            }

            // Resetuj sve regione na 0 za novi ciklus
            for (RegionUtil.Region r : RegionUtil.Region.values()) {
                Map<String, Object> reset = new HashMap<>();
                reset.put("monthlyStars", 0L);
                reset.put("cycleMonthKey", nowKey);
                batch.set(db.collection("region_stats").document(r.naziv), reset, SetOptions.merge());
            }

            // Transakcija na cycle_meta - garantuje da samo jedan klijent "pobedi" trku
            DocumentReference metaRef = db.collection("cycle_meta").document("current");
            db.runTransaction(transaction -> {
                DocumentSnapshot metaSnap = transaction.get(metaRef);
                String checkKey = metaSnap.exists() ? metaSnap.getString("monthKey") : null;
                if (nowKey.equals(checkKey)) {
                    return null; // neko drugi je u međuvremenu već resetovao - odustani
                }
                transaction.set(metaRef, Collections.singletonMap("monthKey", nowKey));
                return null;
            }).addOnSuccessListener(v ->
                    batch.commit()
                            .addOnSuccessListener(v2 -> cb.onSuccess(null))
                            .addOnFailureListener(cb::onError)
            ).addOnFailureListener(cb::onError);

        }).addOnFailureListener(cb::onError);
    }

    /** Mesečna rang lista po regionima - za prikaz u UI */
    public void getMonthlyRegionRanking(Callback<List<Map.Entry<String, Long>>> cb) {
        db.collection("region_stats").get().addOnSuccessListener(snap -> {
            List<Map.Entry<String, Long>> list = new ArrayList<>();
            for (QueryDocumentSnapshot doc : snap) {
                long stars = doc.contains("monthlyStars") ? doc.getLong("monthlyStars") : 0L;
                list.add(new AbstractMap.SimpleEntry<>(doc.getId(), stars));
            }
            list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
            cb.onSuccess(list);
        }).addOnFailureListener(cb::onError);
    }

    /** Statistika za dijalog klikom na region (d) */
    public void getRegionDetailStats(String region, Callback<RegionDetailStats> cb) {
        AggregateQuery registeredQ = db.collection("users")
                .whereEqualTo("region", region).count();
        AggregateQuery activeQ = db.collection("users")
                .whereEqualTo("region", region).whereEqualTo("online", true).count();
        Query goldQ = db.collection("region_history").whereEqualTo("gold", region);
        Query silverQ = db.collection("region_history").whereEqualTo("silver", region);
        Query bronzeQ = db.collection("region_history").whereEqualTo("bronze", region);

        Tasks.whenAllSuccess(
                registeredQ.get(AggregateSource.SERVER),
                activeQ.get(AggregateSource.SERVER),
                goldQ.get(), silverQ.get(), bronzeQ.get()
        ).addOnSuccessListener(results -> {
            AggregateQuerySnapshot registeredSnap = (AggregateQuerySnapshot) results.get(0);
            AggregateQuerySnapshot activeSnap = (AggregateQuerySnapshot) results.get(1);
            QuerySnapshot goldSnap = (QuerySnapshot) results.get(2);
            QuerySnapshot silverSnap = (QuerySnapshot) results.get(3);
            QuerySnapshot bronzeSnap = (QuerySnapshot) results.get(4);

            RegionDetailStats stats = new RegionDetailStats();
            stats.registeredCount = registeredSnap.getCount();
            stats.activeCount = activeSnap.getCount();
            stats.goldCount = goldSnap.size();
            stats.silverCount = silverSnap.size();
            stats.bronzeCount = bronzeSnap.size();
            cb.onSuccess(stats);
        }).addOnFailureListener(cb::onError);
    }

    public static class RegionDetailStats {
        public long registeredCount;
        public long activeCount;
        public int goldCount, silverCount, bronzeCount;
    }
}