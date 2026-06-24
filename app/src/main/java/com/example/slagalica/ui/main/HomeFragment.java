package com.example.slagalica.ui.main;

import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.slagalica.R;
import com.example.slagalica.data.model.DailyMission;
import com.example.slagalica.data.repository.MatchmakingRepository;
import com.example.slagalica.data.repository.MissionRepository;
import com.example.slagalica.ui.games.KoZnaZnaActivity;
import com.example.slagalica.ui.tournament.TournamentActivity;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;

public class HomeFragment extends Fragment {

    private final MissionRepository missionRepo = new MissionRepository();
    private TextView tvMissionWinStatus, tvMissionChatStatus, tvMissionFriendlyStatus, tvMissionTournamentStatus;

    private final MatchmakingRepository matchmakingRepo = new MatchmakingRepository();
    private AlertDialog searchingDialog;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        MaterialButton btnPlay = view.findViewById(R.id.btnPlay);
        btnPlay.setOnClickListener(v -> startRandomMatch());

        MaterialButton btnTournament = view.findViewById(R.id.btnTournament);
        btnTournament.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), TournamentActivity.class))
        );

        tvMissionWinStatus        = view.findViewById(R.id.tvMissionWinStatus);
        tvMissionChatStatus       = view.findViewById(R.id.tvMissionChatStatus);
        tvMissionFriendlyStatus   = view.findViewById(R.id.tvMissionFriendlyStatus);
        tvMissionTournamentStatus = view.findViewById(R.id.tvMissionTournamentStatus);

        loadMissions();

        return view;
    }

    private void startRandomMatch() {
        String myId = FirebaseAuth.getInstance().getUid();
        if (myId == null) return;

        new com.example.slagalica.data.repository.UserRepository().spendToken(
                new com.example.slagalica.data.repository.UserRepository.Callback<Void>() {
                    @Override
                    public void onSuccess(Void r) {
                        showSearchingDialog();
                        matchmakingRepo.findMatch(myId, (matchId, opponentId, isPlayer1) -> {
                            if (searchingDialog != null) searchingDialog.dismiss();
                            Intent intent = new Intent(getActivity(), KoZnaZnaActivity.class);
                            intent.putExtra("isGuest", false);
                            intent.putExtra("matchId", matchId);
                            intent.putExtra("myId", myId);
                            intent.putExtra("opponentId", opponentId);
                            intent.putExtra("isPlayer1", isPlayer1);
                            intent.putExtra("isFriendly", false);
                            startActivity(intent);
                        }, errorMsg -> {
                            if (searchingDialog != null) searchingDialog.dismiss();
                            Toast.makeText(getActivity(), "Greška: " + errorMsg, Toast.LENGTH_SHORT).show();
                        });
                    }
                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showSearchingDialog() {
        searchingDialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Traženje protivnika")
                .setMessage("Tražimo ti protivnika...")
                .setNegativeButton("Otkaži", (d, w) -> matchmakingRepo.cancelSearch())
                .setCancelable(false)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (searchingDialog != null && searchingDialog.isShowing()) {
            matchmakingRepo.cancelSearch();
            searchingDialog.dismiss();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadMissions(); // refresiraj kad se vratis na Home (npr posle partije)
    }

    private void loadMissions() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        missionRepo.getMissions(uid, new MissionRepository.Callback<DailyMission>() {
            @Override
            public void onSuccess(DailyMission m) {
                applyStatus(tvMissionWinStatus, m.isWinMatch());
                applyStatus(tvMissionChatStatus, m.isSendMessage());
                applyStatus(tvMissionFriendlyStatus, m.isFriendlyMatch());
                applyStatus(tvMissionTournamentStatus, m.isWinTournament());
            }
            @Override public void onError(Exception e) {}
        });
    }

    private void applyStatus(TextView tv, boolean done) {
        if (done) {
            tv.setText("✅ +3 ⭐");
            tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            tv.setText("+3 ⭐");
            tv.setPaintFlags(tv.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
        }
    }
}