package com.example.slagalica.ui.notification;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.data.model.AppNotification;
import com.example.slagalica.logic.AppNotificationManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private List<AppNotification> items = new ArrayList<>();
    private final OnNotifActionListener listener;

    public interface OnNotifActionListener {
        void onAcceptInvite(AppNotification item);
        void onDeclineInvite(AppNotification item);
        void onMarkRead(AppNotification item);
    }

    public NotificationAdapter(OnNotifActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<AppNotification> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppNotification item = items.get(position);
        holder.tvMessage.setText(item.getMessage());
        holder.tvTime.setText(new SimpleDateFormat("HH:mm dd.MM.yyyy", Locale.getDefault()).format(new Date(item.getCreatedAt())));
        
        // Icon based on type
        String icon = "🔔";
        String title = "Obaveštenje";
        if (AppNotificationManager.TYPE_GAME_INVITE.equals(item.getType())) {
            icon = "⚔️";
            title = "Poziv za partiju";
        } else if (AppNotificationManager.TYPE_CHAT.equals(item.getType())) {
            icon = "💬";
            title = "Nova poruka";
        } else if (AppNotificationManager.TYPE_REWARD.equals(item.getType())) {
            icon = "🎁";
            title = "Nagrada!";
        }
        
        holder.tvIcon.setText(icon);
        holder.tvTitle.setText(title);

        // Setup visibility based on type
        boolean isInvite = AppNotificationManager.TYPE_GAME_INVITE.equals(item.getType());
        holder.btnAccept.setVisibility(isInvite && !item.isRead() ? View.VISIBLE : View.GONE);
        holder.btnDecline.setVisibility(isInvite && !item.isRead() ? View.VISIBLE : View.GONE);
        holder.btnMarkRead.setVisibility(!item.isRead() && !isInvite ? View.VISIBLE : View.GONE);
        
        holder.tvReadBadge.setText(item.isRead() ? "" : "● NOVO");
        holder.tvReadBadge.setTextColor(0xFFE53935);

        holder.btnAccept.setOnClickListener(v -> listener.onAcceptInvite(item));
        holder.btnDecline.setOnClickListener(v -> listener.onDeclineInvite(item));
        holder.btnMarkRead.setOnClickListener(v -> listener.onMarkRead(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTime, tvIcon, tvTitle, tvReadBadge;
        Button btnAccept, btnDecline, btnMarkRead;
        ViewHolder(View v) {
            super(v);
            tvMessage = v.findViewById(R.id.tvNotifMessage);
            tvTime = v.findViewById(R.id.tvNotifTime);
            tvIcon = v.findViewById(R.id.tvNotifIcon);
            tvTitle = v.findViewById(R.id.tvNotifTitle);
            tvReadBadge = v.findViewById(R.id.tvReadBadge);
            btnAccept = v.findViewById(R.id.btnAccept);
            btnDecline = v.findViewById(R.id.btnDecline);
            btnMarkRead = v.findViewById(R.id.btnMarkRead);
        }
    }
}