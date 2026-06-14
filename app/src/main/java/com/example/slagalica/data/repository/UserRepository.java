package com.example.slagalica.data.repository;

import com.example.slagalica.data.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class UserRepository {
    private static final String USERS = "users";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
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