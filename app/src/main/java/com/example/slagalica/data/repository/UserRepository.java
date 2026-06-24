package com.example.slagalica.data.repository;

import com.example.slagalica.data.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class UserRepository {
    private static final String USERS = "users";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    public void spendToken(Callback<Void> callback) {
        getCurrentUser(new Callback<com.example.slagalica.data.model.User>() {
            @Override
            public void onSuccess(com.example.slagalica.data.model.User user) {
                if (user.getTokens() <= 0) {
                    callback.onError(new Exception("Nemate dovoljno tokena!"));
                    return;
                }
                updateField("tokens", user.getTokens() - 1, callback);
            }
            @Override public void onError(Exception e) { callback.onError(e); }
        });
    }

    public void checkAndGrantDailyTokens(Callback<Void> callback) {
        getCurrentUser(new Callback<com.example.slagalica.data.model.User>() {
            @Override
            public void onSuccess(com.example.slagalica.data.model.User user) {
                long now = System.currentTimeMillis();
                long oneDayMs = 24L * 60 * 60 * 1000;
                if (now - user.getLastDailyTokenGrant() < oneDayMs) {
                    callback.onSuccess(null); // već dobio danas
                    return;
                }
                int league = com.example.slagalica.logic.LeagueLogic.calculateLeague(user.getStars());
                int bonus = com.example.slagalica.logic.LeagueLogic.getBonusTokensPerDay(league);
                int dailyAmount = 5 + bonus;

                int newTokens = user.getTokens() + dailyAmount;
                java.util.Map<String, Object> updates = new java.util.HashMap<>();
                updates.put("tokens", newTokens);
                updates.put("lastDailyTokenGrant", now);

                String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
                db.collection("users").document(uid).update(updates)
                        .addOnSuccessListener(v -> callback.onSuccess(null))
                        .addOnFailureListener(callback::onError);
            }
            @Override public void onError(Exception e) { callback.onError(e); }
        });
    }

    public void getCurrentUser(Callback<User> callback) {
        if (auth.getCurrentUser() == null) {
            callback.onError(new Exception("Nije ulogovan"));
            return;
        }
        String uid = auth.getCurrentUser().getUid();
        db.collection(USERS).document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        User user = doc.toObject(User.class);
                        if (user != null) user.setId(doc.getId());
                        callback.onSuccess(user);
                    } else {
                        callback.onError(new Exception("Korisnik nije pronađen"));
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    public void saveUser(User user, Callback<Void> callback) {
        db.collection(USERS).document(user.getId()).set(user)
                .addOnSuccessListener(v -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    public void updateField(String field, Object value, Callback<Void> callback) {
        String uid = auth.getCurrentUser().getUid();
        db.collection(USERS).document(uid).update(field, value)
                .addOnSuccessListener(v -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }
    public void setOnline(boolean isOnline, Callback<Void> callback) {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();
        Map<String, Object> updates = new HashMap<>();
        updates.put("online", isOnline);
        db.collection(USERS).document(uid).update(updates)
                .addOnSuccessListener(v -> { if (callback != null) callback.onSuccess(null); })
                .addOnFailureListener(e -> { if (callback != null) callback.onError(e); });
    }
    public void updateAvatar(String avatarValue, Callback<Void> callback) {
        updateField("avatarUrl", avatarValue, callback);
    }

    public void getUserById(String userId, Callback<User> callback) {
        db.collection(USERS).document(userId).get()
                .addOnSuccessListener(doc -> {
                    User user = doc.toObject(User.class);
                    if (user != null) user.setId(doc.getId());
                    callback.onSuccess(user);
                })
                .addOnFailureListener(callback::onError);
    }
}