package com.example.slagalica.ui.notification;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalica.R;
import com.example.slagalica.data.model.AppNotification;
import com.example.slagalica.logic.AppNotificationManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.VH> {

    public interface ActionListener {
        void onMarkRead(AppNotification notification);
        void onAcceptInvite(AppNotification notification);
        void onDeclineInvite(AppNotification notification);
        void onClaimReward(AppNotification notification);
    }

    private List<AppNotification> items = new ArrayList<>();
    private final ActionListener listener;
    private static final SimpleDateFormat SDF =
            new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());

    public NotificationAdapter(ActionListener listener) {
        this.listener = listener;
    }

    public void setData(List<AppNotification> data) {
        this.items = data;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        AppNotification n = items.get(position);

        // ── Icon ──────────────────────────────────────────────────────────────
        h.tvIcon.setText(iconFor(n.getType()));

        // ── Title + message ───────────────────────────────────────────────────
        h.tvTitle.setText(titleFor(n.getType()));
        h.tvMessage.setText(n.getMessage());

        // ── Time ──────────────────────────────────────────────────────────────
        h.tvTime.setText(SDF.format(new Date(n.getCreatedAt())));

        // ── Read badge ────────────────────────────────────────────────────────
        if (n.isRead()) {
            h.tvReadBadge.setText("PROČITANO");
            h.tvReadBadge.setTextColor(h.itemView.getContext().getColor(android.R.color.holo_green_dark));
            h.tvTitle.setTypeface(null, Typeface.NORMAL);
        } else {
            h.tvReadBadge.setText("NEPROČITANO");
            h.tvReadBadge.setTextColor(h.itemView.getContext().getColor(android.R.color.holo_red_dark));
            h.tvTitle.setTypeface(null, Typeface.BOLD);
        }

        // ── Card background ───────────────────────────────────────────────────
        int bgColor = n.isRead()
                ? h.itemView.getContext().getColor(R.color.white)
                : h.itemView.getContext().getColor(R.color.primary_green_light);
        h.card.setCardBackgroundColor(bgColor);

        // ── Action buttons ────────────────────────────────────────────────────
        h.btnMarkRead.setVisibility(n.isRead() ? View.GONE : View.VISIBLE);
        h.btnAccept.setVisibility(View.GONE);
        h.btnDecline.setVisibility(View.GONE);
        h.btnClaim.setVisibility(View.GONE);

        switch (n.getType()) {
            case AppNotificationManager.TYPE_GAME_INVITE:
                if (!n.isRead()) {
                    h.btnAccept.setVisibility(View.VISIBLE);
                    h.btnDecline.setVisibility(View.VISIBLE);
                    h.btnMarkRead.setVisibility(View.GONE);
                }
                break;
            case AppNotificationManager.TYPE_REWARD:
                if (!n.isRead()) {
                    h.btnClaim.setVisibility(View.VISIBLE);
                    h.btnMarkRead.setVisibility(View.GONE);
                }
                break;
        }

        // ── Clicks ────────────────────────────────────────────────────────────
        h.btnMarkRead.setOnClickListener(v -> listener.onMarkRead(n));
        h.btnAccept.setOnClickListener(v  -> listener.onAcceptInvite(n));
        h.btnDecline.setOnClickListener(v -> listener.onDeclineInvite(n));
        h.btnClaim.setOnClickListener(v   -> listener.onClaimReward(n));
    }

    @Override
    public int getItemCount() { return items.size(); }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private String iconFor(String type) {
        switch (type) {
            case AppNotificationManager.TYPE_CHAT:        return "💬";
            case AppNotificationManager.TYPE_RANKING:     return "🏆";
            case AppNotificationManager.TYPE_REWARD:      return "🎁";
            case AppNotificationManager.TYPE_GAME_INVITE: return "🎮";
            case AppNotificationManager.TYPE_LEAGUE:      return "⬆️";
            default:                                      return "🔔";
        }
    }

    private String titleFor(String type) {
        switch (type) {
            case AppNotificationManager.TYPE_CHAT:        return "Čet poruka";
            case AppNotificationManager.TYPE_RANKING:     return "Plasman na rang listi";
            case AppNotificationManager.TYPE_REWARD:      return "Osvojena nagrada";
            case AppNotificationManager.TYPE_GAME_INVITE: return "Poziv za partiju";
            case AppNotificationManager.TYPE_LEAGUE:      return "Promena lige";
            default:                                      return "Obaveštenje";
        }
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────
    static class VH extends RecyclerView.ViewHolder {
        CardView card;
        TextView tvIcon, tvTitle, tvMessage, tvTime, tvReadBadge;
        Button btnMarkRead, btnAccept, btnDecline, btnClaim;

        VH(@NonNull View v) {
            super(v);
            card        = v.findViewById(R.id.cardNotification);
            tvIcon      = v.findViewById(R.id.tvNotifIcon);
            tvTitle     = v.findViewById(R.id.tvNotifTitle);
            tvMessage   = v.findViewById(R.id.tvNotifMessage);
            tvTime      = v.findViewById(R.id.tvNotifTime);
            tvReadBadge = v.findViewById(R.id.tvReadBadge);
            btnMarkRead = v.findViewById(R.id.btnMarkRead);
            btnAccept   = v.findViewById(R.id.btnAccept);
            btnDecline  = v.findViewById(R.id.btnDecline);
            btnClaim    = v.findViewById(R.id.btnClaim);
        }
    }
}