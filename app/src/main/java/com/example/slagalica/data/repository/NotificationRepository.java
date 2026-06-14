package com.example.slagalica.data.repository;

import com.example.slagalica.data.model.AppNotification;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationRepository {

    private static final String COLLECTION = "notifications";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface OnSuccess<T> { void run(T result); }
    public interface OnError     { void run(Exception e); }

    // ── Fetch all for user (real-time listener) ───────────────────────────────
    public ListenerRegistration listenForUser(String userId,
                                              OnSuccess<List<AppNotification>> onUpdate,
                                              OnError onError) {
        return db.collection(COLLECTION)
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) { onError.run(e); return; }
                    List<AppNotification> list = new ArrayList<>();
                    if (snap != null) {
                        for (QueryDocumentSnapshot doc : snap) {
                            AppNotification n = doc.toObject(AppNotification.class);
                            n.setId(doc.getId());
                            list.add(n);
                        }
                    }
                    onUpdate.run(list);
                });
    }

    // ── Mark single notification as read ─────────────────────────────────────
    public void markAsRead(String notificationId, OnSuccess<Void> onSuccess, OnError onError) {
        Map<String, Object> update = new HashMap<>();
        update.put("read", true);
        db.collection(COLLECTION).document(notificationId)
                .update(update)
                .addOnSuccessListener(v -> onSuccess.run(null))
                .addOnFailureListener(onError::run);
    }

    // ── Mark ALL unread as read for user ─────────────────────────────────────
    public void markAllAsRead(String userId, OnSuccess<Void> onSuccess, OnError onError) {
        db.collection(COLLECTION)
                .whereEqualTo("userId", userId)
                .whereEqualTo("read", false)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) { onSuccess.run(null); return; }
                    int[] remaining = { snap.size() };
                    for (QueryDocumentSnapshot doc : snap) {
                        Map<String, Object> update = new HashMap<>();
                        update.put("read", true);
                        doc.getReference().update(update)
                                .addOnSuccessListener(v -> {
                                    if (--remaining[0] == 0) onSuccess.run(null);
                                })
                                .addOnFailureListener(onError::run);
                    }
                })
                .addOnFailureListener(onError::run);
    }

    // ── Save new notification ─────────────────────────────────────────────────
    public void save(AppNotification notification, OnSuccess<String> onSuccess, OnError onError) {
        db.collection(COLLECTION)
                .add(notification)
                .addOnSuccessListener(ref -> onSuccess.run(ref.getId()))
                .addOnFailureListener(onError::run);
    }
}