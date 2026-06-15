package com.example.slagalica.ui.friends;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.example.slagalica.R;
import com.example.slagalica.data.model.AppNotification;
import com.example.slagalica.data.model.User;
import com.example.slagalica.data.repository.FriendsRepository;
import com.example.slagalica.data.repository.NotificationRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.logic.AppNotificationManager;
import com.example.slagalica.ui.games.KoZnaZnaActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FriendsFragment extends Fragment {

    private TextInputEditText etSearch;
    private LinearLayout llFriendsList;
    private final UserRepository userRepo = new UserRepository();
    private final FriendsRepository friendsRepo = new FriendsRepository();
    private String myUid;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_friends, container, false);

        myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        etSearch = view.findViewById(R.id.etSearch);
        llFriendsList = view.findViewById(R.id.llFriendsList);

        MaterialButton btnQrScan = view.findViewById(R.id.btnQrScan);
        btnQrScan.setOnClickListener(v ->
                Toast.makeText(getActivity(), "QR skeniranje — dolazi uskoro!", Toast.LENGTH_SHORT).show()
        );

        // Pretraga
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() >= 3) searchUsers(s.toString().trim());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadFriends();
        return view;
    }

    private void searchUsers(String query) {
        FirebaseFirestore.getInstance().collection("users")
                .whereGreaterThanOrEqualTo("username", query)
                .whereLessThanOrEqualTo("username", query + "\uf8ff")
                .get()
                .addOnSuccessListener(snap -> {
                    List<User> results = new ArrayList<>();
                    for (var doc : snap) {
                        User u = doc.toObject(User.class);
                        u.setId(doc.getId());
                        if (!u.getId().equals(myUid)) results.add(u);
                    }
                    showSearchResults(results);
                });
    }
    private void waitForAcceptance(String matchId, String opponentId) {
        FirebaseDatabase.getInstance().getReference("activeMatches")
                .child(matchId).child("info").child("status")
                .addValueEventListener(new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(com.google.firebase.database.DataSnapshot snap) {
                        String status = snap.getValue(String.class);
                        if ("accepted".equals(status)) {
                            Intent intent = new Intent(getActivity(), KoZnaZnaActivity.class);
                            intent.putExtra("isGuest", false);
                            intent.putExtra("matchId", matchId);
                            intent.putExtra("myId", myUid);
                            intent.putExtra("opponentId", opponentId);
                            intent.putExtra("isPlayer1", true);
                            startActivity(intent);
                        }
                    }
                    @Override public void onCancelled(com.google.firebase.database.DatabaseError e) {}
                });
    }

    private String getMyUsername() {
        // jednostavno — koristi email prefix ili "Igrač"
        String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        return email != null ? email.split("@")[0] : "Igrač";
    }
    private void showSearchResults(List<User> users) {
        if (users.isEmpty()) {
            Toast.makeText(getActivity(), "Nema rezultata", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[users.size()];
        for (int i = 0; i < users.size(); i++)
            names[i] = users.get(i).getUsername() + " (" + users.get(i).getRegion() + ")";

        new AlertDialog.Builder(requireContext())
                .setTitle("Rezultati pretrage")
                .setItems(names, (dlg, which) -> {
                    User selected = users.get(which);
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Dodaj prijatelja")
                            .setMessage("Dodati " + selected.getUsername() + " kao prijatelja?")
                            .setPositiveButton("Da", (d, w) -> addFriend(selected))
                            .setNegativeButton("Ne", null)
                            .show();
                })
                .show();
    }

    private void addFriend(User user) {
        friendsRepo.addFriend(myUid, user.getId(), new UserRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void r) {
                Toast.makeText(getActivity(),
                        user.getUsername() + " dodat kao prijatelj!",
                        Toast.LENGTH_SHORT).show();
                loadFriends();
            }
            @Override
            public void onError(Exception e) {
                Toast.makeText(getActivity(),
                        "Greška: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadFriends() {
        friendsRepo.getFriends(myUid, friends -> {
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                llFriendsList.removeAllViews();
                if (friends.isEmpty()) {
                    TextView tvEmpty = new TextView(getActivity());
                    tvEmpty.setText("Nemate prijatelja još uvek.\nPretražite po korisničkom imenu!");
                    tvEmpty.setTextSize(14);
                    tvEmpty.setPadding(16, 16, 16, 16);
                    llFriendsList.addView(tvEmpty);
                    return;
                }
                for (User friend : friends) {
                    addFriendCard(friend);
                }
            });
        }, e -> Toast.makeText(getActivity(), "Greška pri učitavanju", Toast.LENGTH_SHORT).show());
    }

    private void addFriendCard(User friend) {
        View card = LayoutInflater.from(getActivity())
                .inflate(R.layout.item_friend, llFriendsList, false);

        TextView tvName   = card.findViewById(R.id.tvFriendName);
        TextView tvLeague = card.findViewById(R.id.tvFriendLeague);
        TextView tvStatus = card.findViewById(R.id.tvFriendStatus);
        MaterialButton btnPlay = card.findViewById(R.id.btnPlayFriend);

        tvName.setText(friend.getUsername());
        tvLeague.setText("🏆 Liga " + friend.getLeague() + " · " + friend.getStars() + " ⭐");
        tvStatus.setText(friend.isOnline() ? "🟢 Online" : "🔴 Offline");

        btnPlay.setEnabled(friend.isOnline());
        btnPlay.setOnClickListener(v -> {
            String matchId = FirebaseDatabase.getInstance().getReference("activeMatches").push().getKey();

            // Kreiraj match dokument
            Map<String, Object> matchData = new HashMap<>();
            matchData.put("player1", myUid);
            matchData.put("player2", friend.getId());
            matchData.put("status", "pending");
            FirebaseDatabase.getInstance().getReference("activeMatches")
                    .child(matchId).child("info").setValue(matchData);

            // Pošalji notifikaciju pozivu
            AppNotification notif = new AppNotification();
            notif.setUserId(friend.getId());
            notif.setType(AppNotificationManager.TYPE_GAME_INVITE);
            notif.setMessage(getMyUsername() + " te poziva na partiju!");
            notif.setFromUserId(myUid);
            notif.setMatchId(matchId);
            notif.setRead(false);
            notif.setCreatedAt(System.currentTimeMillis());

            new NotificationRepository().save(notif, id -> {
                Toast.makeText(getActivity(), "Poziv poslat! Čekamo prihvatanje...", Toast.LENGTH_SHORT).show();
                // Pozivaoc čeka u KoZnaZna kao Player1
                waitForAcceptance(matchId, friend.getId());
            }, e -> Toast.makeText(getActivity(), "Greška pri slanju poziva", Toast.LENGTH_SHORT).show());
        });

        llFriendsList.addView(card);
    }
}