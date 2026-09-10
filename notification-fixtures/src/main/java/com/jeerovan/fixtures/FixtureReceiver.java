package com.jeerovan.fixtures;
import android.app.*;
import android.content.*;
import android.os.Build;
public class FixtureReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        int id = intent.getIntExtra("id", 1);
        String operation = intent.getStringExtra("operation");
        if ("disable-launch".equals(operation) || "enable-launch".equals(operation)) {
            context.getPackageManager().setComponentEnabledSetting(new ComponentName(context, FixtureActivity.class),
                "disable-launch".equals(operation) ? android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED : android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                android.content.pm.PackageManager.DONT_KILL_APP);
            return;
        }
        if ("clear".equals(operation)) { manager.cancelAll(); return; }
        if ("remove".equals(operation)) { manager.cancel(id); return; }
        String kind = intent.getStringExtra("kind");
        boolean silent = "silent".equals(kind);
        String channel = silent ? "silent" : "alerts";
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(channel, channel, silent ? NotificationManager.IMPORTANCE_LOW : NotificationManager.IMPORTANCE_DEFAULT));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(context, channel) : new Notification.Builder(context);
        PendingIntent open = PendingIntent.getActivity(context, id, new Intent(context, FixtureActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        builder.setSmallIcon(context.getPackageName().endsWith("chat") ? android.R.drawable.ic_dialog_info : android.R.drawable.ic_dialog_email)
            .setContentTitle(intent.getStringExtra("title") == null ? "Fixture " + id : intent.getStringExtra("title"))
            .setContentText(intent.getStringExtra("text") == null ? "Test notification" : intent.getStringExtra("text"))
            .setContentIntent(open).setAutoCancel(true).setWhen(System.currentTimeMillis());
        String group = intent.getStringExtra("group");
        if (group != null) builder.setGroup(group).setGroupSummary("summary".equals(kind));
        if ("ongoing".equals(kind)) builder.setOngoing(true).setCategory(Notification.CATEGORY_SERVICE);
        if ("progress".equals(kind)) builder.setProgress(100, 50, false);
        if ("empty".equals(kind)) builder.setContentTitle(null).setContentText(null);
        if ("message".equals(kind)) builder.setStyle(new Notification.MessagingStyle("Me").setConversationTitle("Team").addMessage("First message", 1L, "Alex").addMessage("Second message", 2L, "Sam"));
        if ("expired".equals(kind)) open.cancel();
        int updates = Math.max(1, Math.min(100, intent.getIntExtra("updates", 1)));
        for (int update = 1; update <= updates; update++) {
            if (updates > 1) builder.setContentText("Update " + update);
            manager.notify(id, builder.build());
        }
    }
}
