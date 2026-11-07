package com.selfattendance;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Daily alarm 1 hour before shift start that posts a permanent (ongoing) notification. */
public class Reminder extends BroadcastReceiver {
    private static final String CHANNEL = "shift";
    private static final int ID = 1;
    private static final int FLAGS = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("attendance", Context.MODE_PRIVATE);
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        if (action == null) {  // our own alarm
            SharedPreferences p = prefs(c);
            if (p.getBoolean("on", false) && !today().equals(p.getString("inDate", ""))) {
                show(c, "Shift starts at " + to12h(p.getString("time", "09:00")),
                        "Don't forget to Check In · tap to open");
            }
        }
        schedule(c);  // next day, or after reboot / app update
    }

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(c, 0, new Intent(c, Reminder.class), FLAGS);
        am.cancel(pi);
        SharedPreferences p = prefs(c);
        if (!p.getBoolean("on", false)) return;

        String[] hm = p.getString("time", "09:00").split(":");
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hm[0]));
        cal.set(Calendar.MINUTE, Integer.parseInt(hm[1]));
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.HOUR_OF_DAY, -1);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1);
        long t = cal.getTimeInMillis();

        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
        } else if (Build.VERSION.SDK_INT >= 23) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, t, pi);
        }
    }

    static void show(Context c, String title, String text) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Shift reminder", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Permanent reminder before your shift starts");
            nm.createNotificationChannel(ch);
            b = new Notification.Builder(c, CHANNEL);
        } else {
            b = new Notification.Builder(c).setPriority(Notification.PRIORITY_HIGH);
        }
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        b.setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                .setContentTitle(title)
                .setContentText(text)
                .setColor(0xFF1D4ED8)
                .setOngoing(true)       // permanent: cannot be swiped away
                .setAutoCancel(false)
                .setContentIntent(PendingIntent.getActivity(c, 0, open, FLAGS));
        nm.notify(ID, b.build());
    }

    static void cancel(Context c) {
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(ID);
    }

    private static String to12h(String hhmm) {
        String[] hm = hhmm.split(":");
        int h = Integer.parseInt(hm[0]);
        return (h % 12 == 0 ? 12 : h % 12) + ":" + hm[1] + (h < 12 ? " AM" : " PM");
    }
}
