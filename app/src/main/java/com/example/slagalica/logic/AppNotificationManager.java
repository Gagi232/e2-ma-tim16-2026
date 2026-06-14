package com.example.slagalica.logic;

import android.app.NotificationChannel;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.slagalica.R;
import com.example.slagalica.data.model.AppNotification;
import com.example.slagalica.data.repository.NotificationRepository;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Centralna klasa za sve notifikacije u aplikaciji.
 * Preimenovana u AppNotificationManager da ne kolidiра sa android.app.NotificationManager.
 *
 * Kanali:
 *   CHANNEL_CHAT     — poruke u četu
 *   CHANNEL_RANKING  — plasman na rang listama
 *   CHANNEL_REWARD   — nagrade (tokeni, zvezde)
 *   CHANNEL_OTHER    — ostalo (liga, pozivi za partiju…)
 */
public class AppNotificationManager {

    // ── Channel IDs ───────────────────────────────────────────────────────────
    public static final String CHANNEL_CHAT    = "channel_chat";
    public static final String CHANNEL_RANKING = "channel_ranking";
    public static final String CHANNEL_REWARD  = "channel_reward";
    public static final String CHANNEL_OTHER   = "channel_other";

    // ── Notification types (stored in Firestore) ──────────────────────────────
    public static final String TYPE_CHAT         = "CHAT";
    public static final String TYPE_RANKING      = "RANKING";
    public static final String TYPE_REWARD       = "REWARD";
    public static final String TYPE_GAME_INVITE  = "GAME_INVITE";
    public static final String TYPE_LEAGUE       = "LEAGUE";
    public static final String TYPE_OTHER        = "OTHER";

    private static int notifIdCounter = 1000;

    private final Context context;
    private final NotificationRepository repo = new NotificationRepository();

    public AppNotificationManager(Context context) {
        this.context = context.getApplicationContext();
        createChannels();
    }

    // ── Channel setup (call once, idempotent) ─────────────────────────────────
    public void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        android.app.NotificationManager nm =
                (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        nm.createNotificationChannel(buildChannel(
                CHANNEL_CHAT, "Čet poruke",
                "Obaveštenja o novim porukama u četu",
                android.app.NotificationManager.IMPORTANCE_HIGH));

        nm.createNotificationChannel(buildChannel(
                CHANNEL_RANKING, "Rang lista",
                "Obaveštenja o plasmanu na rang listama",
                android.app.NotificationManager.IMPORTANCE_DEFAULT));

        nm.createNotificationChannel(buildChannel(
                CHANNEL_REWARD, "Nagrade",
                "Obaveštenja o osvojenim nagradama i tokenima",
                android.app.NotificationManager.IMPORTANCE_DEFAULT));

        nm.createNotificationChannel(buildChannel(
                CHANNEL_OTHER, "Ostalo",
                "Pozivi za partiju, prelazi u ligu i ostala obaveštenja",
                android.app.NotificationManager.IMPORTANCE_DEFAULT));
    }

    private NotificationChannel buildChannel(String id, String name, String desc, int importance) {
        NotificationChannel ch = new NotificationChannel(id, name, importance);
        ch.setDescription(desc);
        return ch;
    }

    // ── Public send methods ───────────────────────────────────────────────────

    public void sendChatNotification(String senderName, String message) {
        String title = "Nova poruka od " + senderName;
        pushSystemNotification(CHANNEL_CHAT, title, message);
        persistNotification(TYPE_CHAT, title + ": " + message);
    }

    public void sendRankingNotification(String rankText) {
        String title = "Plasman na rang listi";
        pushSystemNotification(CHANNEL_RANKING, title, rankText);
        persistNotification(TYPE_RANKING, title + ": " + rankText);
    }

    public void sendRewardNotification(String rewardText) {
        String title = "Osvoji nagradu!";
        pushSystemNotification(CHANNEL_REWARD, title, rewardText);
        persistNotification(TYPE_REWARD, title + ": " + rewardText);
    }

    public void sendGameInviteNotification(String fromUser) {
        String title = "Poziv za partiju";
        String body  = fromUser + " te poziva na partiju!";
        pushSystemNotification(CHANNEL_OTHER, title, body);
        persistNotification(TYPE_GAME_INVITE, body);
    }

    public void sendLeagueNotification(String leagueText) {
        String title = "Promena lige";
        pushSystemNotification(CHANNEL_OTHER, title, leagueText);
        persistNotification(TYPE_LEAGUE, title + ": " + leagueText);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void pushSystemNotification(String channelId, String title, String body) {
        // Use android.R.drawable.ic_dialog_info as fallback — safe, always exists.
        // Replace with R.drawable.ic_notification once you add the vector drawable.
        int iconRes;
        try {
            iconRes = R.drawable.ic_notification;
        } catch (Exception e) {
            iconRes = android.R.drawable.ic_dialog_info;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(iconRes)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat.from(context).notify(notifIdCounter++, builder.build());
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS permission not granted — push skipped, in-app zapis ostaje
        }
    }

    /** Upisuje notifikaciju u Firestore — pojavljuje se u in-app istoriji. */
    private void persistNotification(String type, String message) {
        String uid = currentUserId();
        if (uid == null) return;

        AppNotification n = new AppNotification();
        n.setUserId(uid);
        n.setType(type);
        n.setMessage(message);
        n.setRead(false);
        n.setCreatedAt(System.currentTimeMillis());

        repo.save(n, id -> {}, e -> {});
    }

    private String currentUserId() {
        com.google.firebase.auth.FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }
}