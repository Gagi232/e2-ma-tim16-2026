package com.example.slagalica.ui.games;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.slagalica.R;
import com.example.slagalica.ui.main.ResultsActivity;
import com.google.android.material.button.MaterialButton;

public class MojBrojActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moj_broj);

        MaterialButton btnFinish = findViewById(R.id.btnFinish);
        btnFinish.setOnClickListener(v -> {
            startActivity(new Intent(this, ResultsActivity.class));
            finish();
        });
    }
}