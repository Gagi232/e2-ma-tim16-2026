package com.example.slagalica.ui.friends;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.slagalica.R;
import com.example.slagalica.ui.games.KoZnaZnaActivity;
import com.google.android.material.button.MaterialButton;

public class FriendsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_friends, container, false);

        // Dugme igraj za prijatelja 1
        MaterialButton btnPlay1 = view.findViewById(R.id.btnPlay1);
        btnPlay1.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), KoZnaZnaActivity.class))
        );

        // Dugme igraj za prijatelja 2
        MaterialButton btnPlay2 = view.findViewById(R.id.btnPlay2);
        btnPlay2.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), KoZnaZnaActivity.class))
        );

        // QR skeniranje
        MaterialButton btnQr = view.findViewById(R.id.btnQrScan);
        btnQr.setOnClickListener(v ->
                Toast.makeText(getActivity(), "QR skener - dolazi u KT2!", Toast.LENGTH_SHORT).show()
        );

        return view;
    }
}