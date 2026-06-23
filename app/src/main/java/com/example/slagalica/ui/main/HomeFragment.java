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
import com.example.slagalica.data.repository.MissionRepository;
import com.example.slagalica.ui.games.KoZnaZnaActivity;
import com.example.slagalica.ui.tournament.TournamentActivity;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;

public class HomeFragment extends Fragment {

    private final MissionRepository missionRepo = new MissionRepository();
    private TextView tvMissionWinStatus, tvMissionChatStatus, tvMissionFriendlyStatus, tvMissionTournamentStatus;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        MaterialButton btnPlay = view.findViewById(R.id.btnPlay);
        btnPlay.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), KoZnaZnaActivity.class))
        );
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