package net.kdt.pojavlaunch.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import git.artdeell.mojo.R;

/**
 * VL smart notification channels:
 * - Downloads / installs
 * - Game running
 * - General
 */
public final class VLNotificationHelper {
    public static final String CH_DOWNLOADS = "vl_downloads";
    public static final String CH_GAME = "vl_game";
    public static final String CH_GENERAL = "vl_general";

    private VLNotificationHelper() {}

    public static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;

        NotificationChannel downloads = new NotificationChannel(
                CH_DOWNLOADS, "Downloads & installs", NotificationManager.IMPORTANCE_LOW);
        downloads.setDescription("Modpack, game and runtime downloads");
        downloads.setShowBadge(false);

        NotificationChannel game = new NotificationChannel(
                CH_GAME, "Game session", NotificationManager.IMPORTANCE_DEFAULT);
        game.setDescription("Running game and launch status");

        NotificationChannel general = new NotificationChannel(
                CH_GENERAL, "VL general", NotificationManager.IMPORTANCE_DEFAULT);
        general.setDescription("Accounts, updates and other VL notices");

        nm.createNotificationChannel(downloads);
        nm.createNotificationChannel(game);
        nm.createNotificationChannel(general);
    }

    public static void notifyProgress(Context context, int id, String title, String text, int progress, int max) {
        ensureChannels(context);
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CH_DOWNLOADS)
                .setContentTitle(title != null ? title : "VL")
                .setContentText(text)
                .setSmallIcon(R.drawable.notif_icon)
                .setOnlyAlertOnce(true)
                .setOngoing(progress >= 0 && progress < max);
        if (progress >= 0 && max > 0) {
            b.setProgress(max, progress, false);
        } else {
            b.setProgress(0, 0, true);
        }
        try {
            NotificationManagerCompat.from(context).notify(id, b.build());
        } catch (SecurityException ignored) {}
    }

    public static void notifySimple(Context context, int id, String channel, String title, String text) {
        ensureChannels(context);
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, channel)
                .setContentTitle(title != null ? title : "VL")
                .setContentText(text)
                .setSmallIcon(R.drawable.notif_icon)
                .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(context).notify(id, b.build());
        } catch (SecurityException ignored) {}
    }
}
