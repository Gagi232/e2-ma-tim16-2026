package com.example.slagalica.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.slagalica.R;
import com.example.slagalica.ui.games.KoZnaZnaActivity;
import com.example.slagalica.ui.tournament.TournamentActivity;
import com.google.android.material.button.MaterialButton;

public class HomeFragment extends Fragment {

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

        return view;
    }
}