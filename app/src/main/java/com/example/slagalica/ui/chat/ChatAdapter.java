package com.example.slagalica.ui.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.slagalica.R;
import com.example.slagalica.data.model.ChatMessage;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    private static final int TYPE_MINE  = 0;
    private static final int TYPE_OTHER = 1;

    private final String myUid;
    private List<ChatMessage> messages = new ArrayList<>();

    public ChatAdapter(String myUid) { this.myUid = myUid; }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getSenderId().equals(myUid) ? TYPE_MINE : TYPE_OTHER;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == TYPE_MINE
                ? R.layout.item_chat_mine
                : R.layout.item_chat_other;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);
        holder.tvText.setText(msg.getText());
        holder.tvSender.setText((msg.getSenderAvatar() != null ? msg.getSenderAvatar() : "👤")
                + " " + msg.getSenderUsername());
        String time = new SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault())
                .format(new Date(msg.getTimestamp()));
        holder.tvTime.setText(time);
    }

    @Override
    public int getItemCount() { return messages.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvText, tvSender, tvTime;
        ViewHolder(View v) {
            super(v);
            tvText   = v.findViewById(R.id.tvChatText);
            tvSender = v.findViewById(R.id.tvChatSender);
            tvTime   = v.findViewById(R.id.tvChatTime);
        }
    }
}