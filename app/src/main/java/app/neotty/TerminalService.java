/*
*************************************************************************
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*************************************************************************
*/
package app.neotty;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import app.neotty.termlib.TerminalSession;
import app.neotty.termlib.TerminalSession.SessionChangedCallback;

/**
 * A service holding a list of terminal sessions, {@link #mTerminalSession}, showing a foreground notification while
 * running so that it is not terminated. The user interacts with the session through {@link TerminalActivity}, but this
 * service may outlive the activity when the user or the system disposes of the activity. In that case the user may
 * restart {@link TerminalActivity} later to yet again access the sessions.
 * <p/>
 * In order to keep both terminal sessions and spawned processes (who may outlive the terminal sessions) alive as long
 * as wanted by the user this service is a foreground service, {@link Service#startForeground(int, Notification)}.
 * <p/>
 * Optionally may hold a wake and a wifi lock, in which case that is shown in the notification - see
 * {@link #buildNotification()}.
 */
public class TerminalService extends Service implements SessionChangedCallback {

    private static final String INTENT_ACTION_SERVICE_STOP = "app.neotty.ACTION_STOP_SERVICE";
    private static final String INTENT_ACTION_WAKELOCK_ENABLE = "app.neotty.ACTION_ENABLE_WAKELOCK";
    private static final String INTENT_ACTION_WAKELOCK_DISABLE = "app.neotty.ACTION_DISABLE_WAKELOCK";

    private static final int NOTIFICATION_ID = 1338;
    private static final String NOTIFICATION_CHANNEL_ID = "app.neotty.NOTIFICATION_CHANNEL";

    private TerminalSession mTerminalSession = null;

    private final IBinder mBinder = new LocalBinder();

    public int SSH_PORT = -1;
    public int HTTP_PORT = -1;
    public int HTTPS_PORT = -1;

    /**
     * Note that the service may often outlive the activity, so need to clear this reference.
     */
    SessionChangedCallback mSessionChangeCallback;

    /**
     * If the user has executed the {@link #INTENT_ACTION_SERVICE_STOP} intent.
     */
    boolean mWantsToStop = false;

    /**
     * The wake lock and wifi lock are always acquired and released together.
     */
    private PowerManager.WakeLock mWakeLock;
    private WifiManager.WifiLock mWifiLock;


    private void setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, getString(R.string.application_name), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Notifications from " + getString(R.string.application_name));

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onCreate() {
        setupNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    @Override
    public void onDestroy() {
        if (mWakeLock != null) mWakeLock.release();
        if (mWifiLock != null) mWifiLock.release();
        if (mTerminalSession != null) mTerminalSession.finishIfRunning();
        stopForeground(true);
    }

    @SuppressLint({"Wakelock", "WakelockTimeout"})
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();

        setupNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        if (INTENT_ACTION_SERVICE_STOP.equals(action)) {
            terminateService();
        } else if (INTENT_ACTION_WAKELOCK_ENABLE.equals(action)) {
            if (mWakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, Config.WAKELOCK_LOG_TAG);
                mWakeLock.acquire();

                // http://tools.android.com/tech-docs/lint-in-studio-2-3#TOC-WifiManager-Leak
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                mWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, Config.WAKELOCK_LOG_TAG);
                mWifiLock.acquire();

                updateNotification();
            }
        } else if (INTENT_ACTION_WAKELOCK_DISABLE.equals(action)) {
            if (mWakeLock != null) {
                mWakeLock.release();
                mWakeLock = null;

                mWifiLock.release();
                mWifiLock = null;

                updateNotification();
            }
        } else if (action != null) {
            Log.w(Config.APP_LOG_TAG, "received an unknown action for TerminalService: " + action);
        }

        // If this service really do get killed, there is no point restarting it automatically - let the user do on next
        // start of {@link Term):
        return Service.START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onSessionFinished(final TerminalSession finishedSession) {
        if (mSessionChangeCallback != null) {
            mSessionChangeCallback.onSessionFinished(finishedSession);
        }
    }

    @Override
    public void onTextChanged(TerminalSession changedSession) {
        if (mSessionChangeCallback != null) {
            mSessionChangeCallback.onTextChanged(changedSession);
        }
    }

    @Override
    public void onTitleChanged(TerminalSession changedSession) {
        if (mSessionChangeCallback != null) mSessionChangeCallback.onTitleChanged(changedSession);
        updateNotification();
    }

    @Override
    public void onClipboardText(TerminalSession session, String text) {
        if (mSessionChangeCallback != null) {
            mSessionChangeCallback.onClipboardText(session, text);
        }
    }

    @Override
    public void onBell(TerminalSession session) {
        if (mSessionChangeCallback != null) {
            mSessionChangeCallback.onBell(session);
        }
    }

    public TerminalSession getSession() {
        return mTerminalSession;
    }

    public void setSession(TerminalSession session) {
        mTerminalSession = session;
        updateNotification();
    }

    public void terminateService() {
        mWantsToStop = true;
        if (mTerminalSession != null) mTerminalSession.finishIfRunning();
        stopForeground(true);
        stopSelf();
    }

    private Notification buildNotification() {
        Intent notifyIntent = new Intent(this, TerminalActivity.class);
        // PendingIntent#getActivity(): "Note that the activity will be started outside of the context of an existing
        // activity, so you must use the Intent.FLAG_ACTIVITY_NEW_TASK launch flag in the Intent":
        notifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String contentTitle = "QEMU is not initialized";
        StringBuilder contentText = new StringBuilder();
        contentText.append("Tap to open the application.");

        if (mTerminalSession != null) {
            contentTitle = "QEMU is running";
        }

        final boolean wakeLockHeld = mWakeLock != null;

        if (wakeLockHeld) {
            contentText.append(" Wake lock held.");
        }

        /*if (mTerminalSession != null) {
            String terminalTitle = mTerminalSession.getTitle();
            if (terminalTitle != null && !terminalTitle.isEmpty()) {
                contentText.append("\n" + mTerminalSession.getTitle());
            }
        }*/

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        builder.setContentIntent(pendingIntent);
        builder.setContentTitle(contentTitle);
        builder.setContentText(contentText.toString());
        builder.setSmallIcon(R.drawable.ic_service_notification);
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        builder.setOngoing(true);

        // No need to show a timestamp:
        builder.setShowWhen(false);

        // Background color for small notification icon:
        builder.setColor(0xFF000000);

        String newWakeAction = wakeLockHeld ? INTENT_ACTION_WAKELOCK_DISABLE : INTENT_ACTION_WAKELOCK_ENABLE;
        Intent toggleWakeLockIntent = new Intent(this, TerminalService.class).setAction(newWakeAction);
        String actionTitle = getResources().getString(wakeLockHeld ?
            R.string.notification_action_wake_unlock :
            R.string.notification_action_wake_lock);
        int actionIcon = wakeLockHeld ? android.R.drawable.ic_lock_idle_lock : android.R.drawable.ic_lock_lock;
        builder.addAction(actionIcon, actionTitle, PendingIntent.getService(this, 0, toggleWakeLockIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        return builder.build();
    }

    /**
     * Update the shown foreground service notification after making any changes that affect it.
     */
    public void updateNotification() {
        if (mTerminalSession != null) {
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID, buildNotification());
        }
    }

    /**
     * This service is only bound from inside the same process and never uses IPC.
     */
    public class LocalBinder extends Binder {
        public final TerminalService service = TerminalService.this;
    }
}
