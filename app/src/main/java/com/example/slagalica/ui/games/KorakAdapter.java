package com.example.slagalica.ui.games;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.data.model.Korak;
import com.example.slagalica.data.model.KorakState;

import java.util.List;

public class KorakAdapter extends RecyclerView.Adapter<KorakAdapter.KorakViewHolder> {
    private final List<Korak> koraci;

    public KorakAdapter(List<Korak> koraci) {
        this.koraci = koraci;
    }

    @NonNull
    @Override
    public KorakViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_korak, parent, false);
        return new KorakViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull KorakViewHolder holder, int position) {
        Korak korak = koraci.get(position);
        holder.tvNumber.setText(String.valueOf(korak.getRedniBroj()));

        if (korak.getState() == KorakState.ZAKLJUCAN) {
            holder.tvHint.setText(holder.itemView.getContext().getString(R.string.korak_locked));
            holder.card.setCardBackgroundColor(Color.parseColor("#E0E0E0"));
        } else if (korak.getState() == KorakState.OTVOREN) {
            holder.tvHint.setText(korak.getHint());
            holder.card.setCardBackgroundColor(Color.parseColor("#FFFFFF"));
        } else if (korak.getState() == KorakState.POGODJEN) {
            holder.tvHint.setText(korak.getHint());
            holder.card.setCardBackgroundColor(Color.parseColor("#C8E6C9"));
        } else if (korak.getState() == KorakState.PROMASEN) {
            holder.tvHint.setText(korak.getHint());
            holder.card.setCardBackgroundColor(Color.parseColor("#FFCDD2"));
        }
    }

    @Override
    public int getItemCount() {
        return koraci.size();
    }

    public void otvoriSledeci() {
        for (int i = 0; i < koraci.size(); i++) {
            if (koraci.get(i).getState() == KorakState.ZAKLJUCAN) {
                koraci.get(i).setState(KorakState.OTVOREN);
                notifyItemChanged(i);
                return;
            }
        }
    }

    static class KorakViewHolder extends RecyclerView.ViewHolder {
        final TextView tvNumber;
        final TextView tvHint;
        final CardView card;

        KorakViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNumber = itemView.findViewById(R.id.tvNumber);
            tvHint = itemView.findViewById(R.id.tvHint);
            card = (CardView) itemView;
        }
    }
}
