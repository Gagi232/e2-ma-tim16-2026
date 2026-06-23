package com.example.slagalica.ui.notification;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.data.model.AppNotification;
import com.example.slagalica.data.repository.NotificationRepository;
import com.example.slagalica.ui.games.KoZnaZnaActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class NotificationsFragment extends Fragment {

    private NotificationAdapter adapter;
    private final NotificationRepository repo = new NotificationRepository();
    private ListenerRegistration registration;
    private List<AppNotification> allNotifications = new ArrayList<>();
    private String myUid;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_notifications, container, false);
        myUid = FirebaseAuth.getInstance().getUid();

        RecyclerView rvNotifications = v.findViewById(R.id.rvNotifications);
        adapter = new NotificationAdapter(new NotificationAdapter.OnNotifActionListener() {
            @Override
            public void onAcceptInvite(AppNotification item) {
                acceptGameInvite(item);
            }

            @Override
            public void onDeclineInvite(AppNotification item) {
                repo.markAsRead(item.getId(), r -> {}, e -> {});
                FirebaseDatabase.getInstance().getReference("activeMatches")
                        .child(item.getMatchId()).child("info").child("status").setValue("declined");
            }

            @Override
            public void onMarkRead(AppNotification item) {
                repo.markAsRead(item.getId(), r -> {}, e -> {});
            }
        });
        rvNotifications.setAdapter(adapter);

        v.findViewById(R.id.btnAll).setOnClickListener(view -> adapter.setItems(allNotifications));
        v.findViewById(R.id.btnRead).setOnClickListener(view -> {
            List<AppNotification> filtered = new ArrayList<>();
            for (AppNotification n : allNotifications) if (n.isRead()) filtered.add(n);
            adapter.setItems(filtered);
        });
        v.findViewById(R.id.btnUnread).setOnClickListener(view -> {
            List<AppNotification> filtered = new ArrayList<>();
            for (AppNotification n : allNotifications) if (!n.isRead()) filtered.add(n);
            adapter.setItems(filtered);
        });

        return v;
    }

    private void acceptGameInvite(AppNotification item) {
        if (item.getMatchId() == null) return;

        // Update Firebase Realtime Status
        FirebaseDatabase.getInstance().getReference("activeMatches")
                .child(item.getMatchId()).child("info").child("status").setValue("accepted")
                .addOnFailureListener(e -> {
                    if (isAdded() && getActivity() != null)
                        Toast.makeText(getActivity(), "Greška pri prihvatanju: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });

        // Mark notification as read
        repo.markAsRead(item.getId(), r -> {
            if (!isAdded() || getActivity() == null) return;   // ⬅ FIX: fragment možda nije više attach-ovan

            Intent intent = new Intent(getActivity(), KoZnaZnaActivity.class);
            intent.putExtra("isGuest", false);
            intent.putExtra("matchId", item.getMatchId());
            intent.putExtra("myId", myUid);
            intent.putExtra("opponentId", item.getFromUserId());
            intent.putExtra("isPlayer1", false); // Invited person is Player 2
            intent.putExtra("isFriendly", true);
            startActivity(intent);
        }, e -> {
            if (isAdded() && getActivity() != null)
                Toast.makeText(getActivity(), "Greška", Toast.LENGTH_SHORT).show();
        });
    }
    @Override
    public void onStart() {
        super.onStart();
        if (myUid != null) {
            registration = repo.listenForUser(myUid, list -> {
                if (getActivity() == null) return;
                allNotifications = list;
                adapter.setItems(list);
                if (list.isEmpty()) {
                    Toast.makeText(getActivity(), "Nemate novih obaveštenja", Toast.LENGTH_SHORT).show();
                }
            }, e -> {
                if (getActivity() != null) {
                    Toast.makeText(getActivity(), "Greška: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (registration != null) registration.remove();
    }
}