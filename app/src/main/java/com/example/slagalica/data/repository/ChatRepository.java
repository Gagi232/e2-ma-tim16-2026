package com.example.slagalica.data.repository;

import com.example.slagalica.data.model.ChatMessage;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class ChatRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface OnMessages { void onUpdate(List<ChatMessage> messages); }
    public interface OnError    { void onError(Exception e); }

    public ListenerRegistration listenForRegion(String region, OnMessages onUpdate, OnError onError) {
        return db.collection("chat")
                .whereEqualTo("region", region)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .limit(100)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) { onError.onError(e); return; }
                    List<ChatMessage> list = new ArrayList<>();
                    if (snap != null) {
                        for (QueryDocumentSnapshot doc : snap) {
                            ChatMessage msg = doc.toObject(ChatMessage.class);
                            msg.setId(doc.getId());
                            list.add(msg);
                        }
                    }
                    onUpdate.onUpdate(list);
                });
    }

    public void sendMessage(ChatMessage message, Runnable onSuccess, OnError onError) {
        db.collection("chat")
                .add(message)
                .addOnSuccessListener(ref -> onSuccess.run())
                .addOnFailureListener(onError::onError);
    }
}