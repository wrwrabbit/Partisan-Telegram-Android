package org.telegram.messenger.partisan;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import org.telegram.messenger.AppStartReceiver;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.fakepasscode.FakePasscodeUtils;
import org.telegram.messenger.partisan.secretgroups.EncryptedGroupInnerChatStarter;

public class InnerPartisanTimer implements Runnable {
    private static InnerPartisanTimer instance;

    public static synchronized InnerPartisanTimer getInstance() {
        if (instance == null) {
            instance = new InnerPartisanTimer();
        }
        return instance;
    }

    public void schedule(Context context) {
        try {
            scheduleAlarm(context);
            scheduleRunnable();
        } catch (Exception ignore) {
        }
    }

    private static void scheduleAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AppStartReceiver.class);
        int flags = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent pintent = PendingIntent.getBroadcast(context, 0, intent, flags);
        alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 60 * 1000, 5 * 60 * 1000, pintent);
    }

    private synchronized void scheduleRunnable() {
        Utilities.globalQueue.postRunnable(this, 1000);
    }

    @Override
    public void run() {
        executeScheduledActions();
        scheduleRunnable();
    }

    public void executeScheduledActions() {
        FakePasscodeUtils.tryActivateByTimer();
        EncryptedGroupInnerChatStarter.checkForAllAccounts();
    }
}
