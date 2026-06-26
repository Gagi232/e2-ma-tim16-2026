package com.example.slagalica.ui.chat;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.slagalica.R;
import com.example.slagalica.data.model.AppNotification;
import com.example.slagalica.data.model.ChatMessage;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.ChatRepository;
import com.example.slagalica.data.repository.NotificationRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.logic.AppNotificationManager;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class ChatActivity extends AppCompatActivity {

    private RecyclerView rvChat;
    private TextInputEditText etMessage;
    private ChatAdapter adapter;
    private LinearLayoutManager layoutManager;

    private final ChatRepository chatRepo = new ChatRepository();
    private final UserRepository userRepo = new UserRepository();
    private final NotificationRepository notifRepo = new NotificationRepository();
    private ListenerRegistration chatListener;

    private String myUid, myRegion, myUsername, myAvatar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        rvChat    = findViewById(R.id.rvChat);
        etMessage = findViewById(R.id.etMessage);

        layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvChat.setLayoutManager(layoutManager);

        myUid = FirebaseAuth.getInstance().getUid();
        adapter = new ChatAdapter(myUid);
        rvChat.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSend).setOnClickListener(v -> sendMessage());

        userRepo.getCurrentUser(new UserRepository.Callback<User>() {
            @Override
            public void onSuccess(User user) {
                myRegion   = user.getRegion();
                myUsername = user.getUsername();
                myAvatar   = user.getAvatarUrl();

                if (myRegion == null) {
                    Toast.makeText(ChatActivity.this,
                            "Niste odabrali region pri registraciji.", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }

                ((TextView) findViewById(R.id.tvChatTitle))
                        .setText("Čet — " + myRegion);

                startListening();
            }
            @Override
            public void onError(Exception e) { finish(); }
        });
    }

    private void startListening() {
        chatListener = chatRepo.listenForRegion(myRegion, messages -> {
            adapter.setMessages(messages);
            rvChat.scrollToPosition(messages.size() - 1);
        }, e -> Toast.makeText(this, "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void sendMessage() {
        String text = etMessage.getText() != null
                ? etMessage.getText().toString().trim() : "";
        if (text.isEmpty()) return;

        ChatMessage msg = new ChatMessage();
        msg.setSenderId(myUid);
        msg.setSenderUsername(myUsername);
        msg.setSenderAvatar(myAvatar);
        msg.setText(text);
        msg.setTimestamp(System.currentTimeMillis());
        msg.setRegion(myRegion);

        etMessage.setText("");

        chatRepo.sendMessage(msg, () -> {}, e ->
                Toast.makeText(this, "Greška pri slanju.", Toast.LENGTH_SHORT).show());

        notifyRegionMembers(text);
    }

    private void notifyRegionMembers(String text) {
        FirebaseFirestore.getInstance().collection("users")
                .whereEqualTo("region", myRegion)
                .whereEqualTo("online", false)
                .get()
                .addOnSuccessListener(snap -> {
                    for (QueryDocumentSnapshot doc : snap) {
                        String uid = doc.getId();
                        if (uid.equals(myUid)) continue;

                        AppNotification notif = new AppNotification();
                        notif.setUserId(uid);
                        notif.setType(AppNotificationManager.TYPE_CHAT);
                        notif.setMessage(myUsername + ": " + text);
                        notif.setRead(false);
                        notif.setCreatedAt(System.currentTimeMillis());
                        notifRepo.save(notif, id -> {}, e -> {});
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatListener != null) chatListener.remove();
    }
}