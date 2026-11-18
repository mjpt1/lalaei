package ir.lalaei.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

// Foreground service that keeps the app alive while a lullaby plays with the screen off.
// The audio itself plays in the WebView; this only holds the notification.
public class PlaybackService extends Service {
    static final String CH = "play";

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        String title = i != null ? i.getStringExtra("title") : null;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CH) == null) {
            NotificationChannel c = new NotificationChannel(CH, "پخش لالایی", NotificationManager.IMPORTANCE_LOW);
            c.setShowBadge(false);
            nm.createNotificationChannel(c);
        }
        Intent open = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
        Intent tog = new Intent(this, MainActivity.class).setAction("toggle").setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pt = PendingIntent.getActivity(this, 1, tog, PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CH)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title == null || title.isEmpty() ? "لالایی" : title)
                .setContentText("در حال پخش")
                .setContentIntent(pi)
                .addAction(new Notification.Action.Builder(null, "توقف", pt).build())
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else
            startForeground(1, n);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent i) { return null; }
}
