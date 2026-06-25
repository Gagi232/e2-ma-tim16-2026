package com.example.slagalica.ui.friends;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
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
import com.example.slagalica.data.repository.CycleLeaderboardRepository;
import com.example.slagalica.data.repository.FriendsRepository;
import com.example.slagalica.data.repository.NotificationRepository;
import com.example.slagalica.data.repository.UserRepository;
import com.example.slagalica.logic.AppNotificationManager;
import com.example.slagalica.ui.games.KoZnaZnaActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import androidx.activity.result.ActivityResultLauncher;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

public class FriendsFragment extends Fragment {

    private TextInputEditText etSearch;
    private LinearLayout llFriendsList;
    private final UserRepository userRepo = new UserRepository();
    private final FriendsRepository friendsRepo = new FriendsRepository();
    private String myUid;
    private AlertDialog waitingDialog;
    private ValueEventListener matchListener;
    private String currentMatchId;

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
        btnQrScan.setOnClickListener(v -> startQrScan());

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

    private void startQrScan() {
        if (getContext() == null) return;

        if (androidx.core.content.ContextCompat.checkSelfPermission(getContext(), android.Manifest.permission.CAMERA)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, 200);
            return;
        }

        com.journeyapps.barcodescanner.ScanOptions options = new com.journeyapps.barcodescanner.ScanOptions();
        options.setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE);
        options.setPrompt("Skenirajte QR kod prijatelja");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        qrLauncher.launch(options);
    }

    private final androidx.activity.result.ActivityResultLauncher<com.journeyapps.barcodescanner.ScanOptions> qrLauncher =
            registerForActivityResult(new com.journeyapps.barcodescanner.ScanContract(), result -> {
                if (result.getContents() == null) {
                    return; // korisnik je otkazao skeniranje
                }
                handleScannedQr(result.getContents());
            });

    private void handleScannedQr(String content) {
        String prefix = "slagalica://friend/";
        if (!content.startsWith(prefix)) {
            Toast.makeText(getActivity(), "Ovo nije Slagalica QR kod prijatelja.", Toast.LENGTH_SHORT).show();
            return;
        }
        String scannedUid = content.substring(prefix.length());

        if (scannedUid.equals(myUid)) {
            Toast.makeText(getActivity(), "Ne možete dodati sebe kao prijatelja.", Toast.LENGTH_SHORT).show();
            return;
        }

        userRepo.getUserById(scannedUid, new UserRepository.Callback<User>() {
            @Override
            public void onSuccess(User scannedUser) {
                if (scannedUser == null) {
                    Toast.makeText(getActivity(), "Korisnik nije pronađen.", Toast.LENGTH_SHORT).show();
                    return;
                }
                new AlertDialog.Builder(requireContext())
                        .setTitle("Dodaj prijatelja")
                        .setMessage("Dodati " + scannedUser.getUsername() + " kao prijatelja?")
                        .setPositiveButton("Da", (d, w) -> addFriend(scannedUser))
                        .setNegativeButton("Ne", null)
                        .show();
            }
            @Override
            public void onError(Exception e) {
                Toast.makeText(getActivity(), "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
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
                Toast.makeText(getActivity(), user.getUsername() + " dodat kao prijatelj!", Toast.LENGTH_SHORT).show();
                loadFriends();
            }
            @Override
            public void onError(Exception e) {
                Toast.makeText(getActivity(), "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
                for (User friend : friends) addFriendCard(friend);
            });
        }, e -> Toast.makeText(getActivity(), "Greška pri učitavanju", Toast.LENGTH_SHORT).show());
    }

    private interface BusyCallback { void result(boolean isBusy); }

    private void checkIfFriendBusy(User friend, BusyCallback callback) {
        FirebaseDatabase.getInstance().getReference("activeMatches")
                .orderByChild("info/player1")
                .equalTo(friend.getId())
                .get()
                .addOnSuccessListener(snap1 -> {
                    if (snap1.exists()) { callback.result(true); return; }
                    FirebaseDatabase.getInstance().getReference("activeMatches")
                            .orderByChild("info/player2")
                            .equalTo(friend.getId())
                            .get()
                            .addOnSuccessListener(snap2 -> callback.result(snap2.exists()))
                            .addOnFailureListener(e -> callback.result(false)); // ne blokiraj ako padne provera
                })
                .addOnFailureListener(e -> callback.result(false));
    }

    private void addFriendCard(User friend) {
        View card = LayoutInflater.from(getActivity()).inflate(R.layout.item_friend, llFriendsList, false);

        String avatar = friend.getAvatarUrl();
        TextView tvFriendAvatar = card.findViewById(R.id.tvFriendAvatar); // treba dodati ovaj ID u item_friend.xml
        if (tvFriendAvatar != null) {
            tvFriendAvatar.setText(avatar != null && !avatar.isEmpty() ? avatar : "👤");
        }

        ((TextView) card.findViewById(R.id.tvFriendName)).setText(friend.getUsername());

        int league = friend.getLeague();
        String leagueText = com.example.slagalica.logic.LeagueLogic.getLeagueIcon(league) + " "
                + com.example.slagalica.logic.LeagueLogic.getLeagueName(league)
                + " · " + friend.getStars() + " ⭐";
        ((TextView) card.findViewById(R.id.tvFriendLeague)).setText(leagueText);

        TextView tvFriendRank = card.findViewById(R.id.tvFriendRank);
        if (tvFriendRank != null) {
            tvFriendRank.setText("📊 Učitavam...");
            loadFriendMonthlyRank(friend.getId(), tvFriendRank);
        }

        ((TextView) card.findViewById(R.id.tvFriendStatus)).setText(friend.isOnline() ? "🟢 Online" : "🔴 Offline");

        MaterialButton btnPlay = card.findViewById(R.id.btnPlayFriend);
        btnPlay.setEnabled(false); // default disabled dok ne provеримo da nije u partiji
        checkIfFriendBusy(friend, isBusy -> {
            btnPlay.setEnabled(friend.isOnline() && !isBusy);
        });
        btnPlay.setOnClickListener(v -> startMatchInvite(friend));

        llFriendsList.addView(card);
    }

    private void startMatchInvite(User friend) {
        String matchId = FirebaseDatabase.getInstance().getReference("activeMatches").push().getKey();
        if (matchId == null) return;
        currentMatchId = matchId;

        Map<String, Object> matchData = new HashMap<>();
        matchData.put("player1", myUid);
        matchData.put("player2", friend.getId());
        matchData.put("status", "pending");
        matchData.put("isFriendly", true);

        FirebaseDatabase.getInstance().getReference("activeMatches")
                .child(matchId).child("info").setValue(matchData)
                .addOnSuccessListener(v -> {
                    // Tek SADA, kad je info sigurno na serveru, nastavljamo dalje
                    sendInviteNotification(friend, matchId);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getActivity(), "Greška pri kreiranju partije: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void sendInviteNotification(User friend, String matchId) {
        userRepo.getCurrentUser(new UserRepository.Callback<User>() {
            @Override
            public void onSuccess(User me) {
                AppNotification notif = new AppNotification();
                notif.setUserId(friend.getId());
                notif.setType(AppNotificationManager.TYPE_GAME_INVITE);
                notif.setMessage(me.getUsername() + " te poziva na partiju!");
                notif.setFromUserId(myUid);
                notif.setFromUsername(me.getUsername());
                notif.setMatchId(matchId);
                notif.setRead(false);
                notif.setCreatedAt(System.currentTimeMillis());

                new NotificationRepository().save(notif,
                        id -> showWaitingDialog(friend, matchId),
                        e -> Toast.makeText(getActivity(), "Greška pri slanju", Toast.LENGTH_SHORT).show());
            }
            @Override public void onError(Exception e) {}
        });
    }

    private void showWaitingDialog(User friend, String matchId) {
        if (getActivity() == null) return;

        waitingDialog = new AlertDialog.Builder(getActivity())
                .setTitle("Poziv poslat")
                .setMessage("Čekamo da " + friend.getUsername() + " prihvati... (10s)")
                .setNegativeButton("Otkaži", (d, w) -> cancelInvite(matchId))
                .setCancelable(false)
                .show();

        // 10s Timer
        new CountDownTimer(10000, 1000) {
            @Override public void onTick(long ms) {
                if (waitingDialog != null && waitingDialog.isShowing()) {
                    waitingDialog.setMessage("Čekamo da " + friend.getUsername() + " prihvati... (" + (ms/1000) + "s)");
                }
            }
            @Override public void onFinish() {
                if (waitingDialog != null && waitingDialog.isShowing()) {
                    cancelInvite(matchId);
                    Toast.makeText(getActivity(), "Vreme za poziv je isteklo.", Toast.LENGTH_SHORT).show();
                }
            }
        }.start();

        listenForAcceptance(matchId, friend.getId());
    }

    private void loadFriendMonthlyRank(String friendId, TextView tvRank) {
        new CycleLeaderboardRepository().getMonthlyRanking(
                new CycleLeaderboardRepository.Callback<java.util.List<com.example.slagalica.data.model.CycleEntry>>() {
                    @Override
                    public void onSuccess(java.util.List<com.example.slagalica.data.model.CycleEntry> entries) {
                        for (int i = 0; i < entries.size(); i++) {
                            if (friendId.equals(entries.get(i).getUserId())) {
                                int rank = i + 1;
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() ->
                                            tvRank.setText("📊 #" + rank + " mesečno"));
                                }
                                return;
                            }
                        }
                        // nije rangiran ovog meseca
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> tvRank.setText("📊 Nije rangiran"));
                        }
                    }
                    @Override
                    public void onError(Exception e) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> tvRank.setText("📊 —"));
                        }
                    }
                });
    }
    private void listenForAcceptance(String matchId, String opponentId) {
        matchListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {
                String status = snap.getValue(String.class);
                if ("accepted".equals(status)) {
                    cleanupMatchListener();
                    if (waitingDialog != null) waitingDialog.dismiss();
                    
                    Intent intent = new Intent(getActivity(), KoZnaZnaActivity.class);
                    intent.putExtra("isGuest", false);
                    intent.putExtra("matchId", matchId);
                    intent.putExtra("myId", myUid);
                    intent.putExtra("opponentId", opponentId);
                    intent.putExtra("isPlayer1", true);
                    intent.putExtra("isFriendly", true);
                    startActivity(intent);
                } else if ("declined".equals(status)) {
                    cleanupMatchListener();
                    if (waitingDialog != null) waitingDialog.dismiss();
                    Toast.makeText(getActivity(), "Protivnik je odbio poziv.", Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        FirebaseDatabase.getInstance().getReference("activeMatches")
                .child(matchId).child("info").child("status").addValueEventListener(matchListener);
    }

    private void cancelInvite(String matchId) {
        cleanupMatchListener();
        FirebaseDatabase.getInstance().getReference("activeMatches")
                .child(matchId).child("info").child("status").setValue("cancelled");
        if (waitingDialog != null) waitingDialog.dismiss();
    }

    private void cleanupMatchListener() {
        if (matchListener != null && currentMatchId != null) {
            FirebaseDatabase.getInstance().getReference("activeMatches")
                    .child(currentMatchId).child("info").child("status").removeEventListener(matchListener);
        }
        matchListener = null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cleanupMatchListener();
    }
}