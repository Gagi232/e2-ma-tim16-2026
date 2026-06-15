package com.example.slagalica.ui.notification;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.data.model.AppNotification;
import com.example.slagalica.data.repository.NotificationRepository;
import com.example.slagalica.logic.AppNotificationManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class NotificationsFragment extends Fragment
        implements NotificationAdapter.ActionListener {

    // ── Filter states ─────────────────────────────────────────────────────────
    private enum Filter { ALL, READ, UNREAD }

    // ── UI ────────────────────────────────────────────────────────────────────
    private RecyclerView recyclerView;
    private Button btnAll, btnRead, btnUnread;

    // ── Data ──────────────────────────────────────────────────────────────────
    private NotificationAdapter adapter;
    private final NotificationRepository repo = new NotificationRepository();
    private List<AppNotification> allNotifications = new ArrayList<>();
    private Filter currentFilter = Filter.ALL;
    private ListenerRegistration listenerReg;

    // ─────────────────────────────────────────────────────────────────────────
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notifications, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button btnTest = view.findViewById(R.id.btnTestNotif);
        btnTest.setOnClickListener(v -> {
            AppNotificationManager manager = new AppNotificationManager(requireContext());
            manager.sendGameInviteNotification("TestIgrac");
            manager.sendRewardNotification("Osvojio si 50 tokena!");
            manager.sendRankingNotification("Tvoj plasman je #5 na rang listi.");
            Toast.makeText(requireContext(), "Test notifikacije poslate!", Toast.LENGTH_SHORT).show();
        });

        bindViews(view);
        setupRecyclerView();
        setupFilterButtons();
        loadNotifications();
    }

    // ── Bind ──────────────────────────────────────────────────────────────────
    private void bindViews(View v) {
        recyclerView = v.findViewById(R.id.rvNotifications);
        btnAll       = v.findViewById(R.id.btnAll);
        btnRead      = v.findViewById(R.id.btnRead);
        btnUnread    = v.findViewById(R.id.btnUnread);
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────
    private void setupRecyclerView() {
        adapter = new NotificationAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
    }

    // ── Filter buttons ────────────────────────────────────────────────────────
    private void setupFilterButtons() {
        btnAll.setOnClickListener(v    -> applyFilter(Filter.ALL));
        btnRead.setOnClickListener(v   -> applyFilter(Filter.READ));
        btnUnread.setOnClickListener(v -> applyFilter(Filter.UNREAD));
        highlightFilter(Filter.ALL);
    }

    private void applyFilter(Filter filter) {
        currentFilter = filter;
        highlightFilter(filter);
        renderFiltered();
    }

    private void highlightFilter(Filter active) {
        int on  = requireContext().getColor(R.color.primary_green);
        int off = requireContext().getColor(android.R.color.darker_gray);
        btnAll.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                active == Filter.ALL    ? on : off));
        btnRead.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                active == Filter.READ   ? on : off));
        btnUnread.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                active == Filter.UNREAD ? on : off));
    }

    // ── Load from Firestore (real-time) ───────────────────────────────────────
    private void loadNotifications() {
        String uid = currentUid();
        if (uid == null) return;

        listenerReg = repo.listenForUser(uid,
                notifications -> {
                    allNotifications = notifications;
                    renderFiltered();
                },
                e -> Toast.makeText(requireContext(),
                        "Greška pri učitavanju obaveštenja.", Toast.LENGTH_SHORT).show()
        );
    }

    private void renderFiltered() {
        List<AppNotification> filtered;
        switch (currentFilter) {
            case READ:
                filtered = allNotifications.stream()
                        .filter(AppNotification::isRead)
                        .collect(Collectors.toList());
                break;
            case UNREAD:
                filtered = allNotifications.stream()
                        .filter(n -> !n.isRead())
                        .collect(Collectors.toList());
                break;
            default:
                filtered = new ArrayList<>(allNotifications);
                break;
        }
        adapter.setData(filtered);
    }

    // ── ActionListener ────────────────────────────────────────────────────────
    @Override
    public void onMarkRead(AppNotification notification) {
        repo.markAsRead(notification.getId(),
                v -> { /* real-time listener will refresh the list */ },
                e -> Toast.makeText(requireContext(), "Greška.", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onAcceptInvite(AppNotification notification) {
        // Mark as read so it disappears from unread
        repo.markAsRead(notification.getId(), v -> {}, e -> {});
        Toast.makeText(requireContext(), "Prihvaćen poziv!", Toast.LENGTH_SHORT).show();
        // TODO: navigate to matchmaking / start game with sender
    }

    @Override
    public void onDeclineInvite(AppNotification notification) {
        repo.markAsRead(notification.getId(), v -> {}, e -> {});
        Toast.makeText(requireContext(), "Poziv odbijen.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onClaimReward(AppNotification notification) {
        repo.markAsRead(notification.getId(), v -> {}, e -> {});
        Toast.makeText(requireContext(), "Nagrada preuzeta!", Toast.LENGTH_SHORT).show();
        // TODO: call MatchManager / UserRepository to actually credit tokens/stars
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private String currentUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (listenerReg != null) listenerReg.remove();
    }
}