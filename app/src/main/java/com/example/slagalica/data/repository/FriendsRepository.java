package com.example.slagalica.data.repository;

import com.example.slagalica.data.model.User;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FriendsRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface OnFriendsLoaded {
        void onLoaded(List<User> friends);
    }

    public interface OnError {
        void onError(Exception e);
    }

    public void addFriend(String myUid, String friendUid, UserRepository.Callback<Void> callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("friendId", friendUid);
        data.put("addedAt", System.currentTimeMillis());

        db.collection("users").document(myUid)
                .collection("friends").document(friendUid)
                .set(data)
                .addOnSuccessListener(v -> {
                    // Dodaj i obrnuto
                    Map<String, Object> reverse = new HashMap<>();
                    reverse.put("friendId", myUid);
                    reverse.put("addedAt", System.currentTimeMillis());
                    db.collection("users").document(friendUid)
                            .collection("friends").document(myUid)
                            .set(reverse)
                            .addOnSuccessListener(v2 -> callback.onSuccess(null))
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    public void getFriends(String myUid, OnFriendsLoaded onLoaded, OnError onError) {
        db.collection("users").document(myUid)
                .collection("friends").get()
                .addOnSuccessListener(snap -> {
                    List<String> friendIds = new ArrayList<>();
                    for (DocumentSnapshot doc : snap) {
                        friendIds.add(doc.getId());
                    }
                    if (friendIds.isEmpty()) {
                        onLoaded.onLoaded(new ArrayList<>());
                        return;
                    }
                    loadFriendUsers(friendIds, onLoaded, onError);
                })
                .addOnFailureListener(onError::onError);
    }

    private void loadFriendUsers(List<String> ids, OnFriendsLoaded onLoaded, OnError onError) {
        List<User> result = new ArrayList<>();
        int[] done = {0};
        for (String id : ids) {
            db.collection("users").document(id).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            User u = doc.toObject(User.class);
                            u.setId(doc.getId());
                            result.add(u);
                        }
                        if (++done[0] == ids.size()) onLoaded.onLoaded(result);
                    })
                    .addOnFailureListener(e -> {
                        if (++done[0] == ids.size()) onLoaded.onLoaded(result);
                    });
        }
    }

    public void removeFriend(String myUid, String friendUid, UserRepository.Callback<Void> callback) {
        db.collection("users").document(myUid)
                .collection("friends").document(friendUid)
                .delete()
                .addOnSuccessListener(v -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }
}