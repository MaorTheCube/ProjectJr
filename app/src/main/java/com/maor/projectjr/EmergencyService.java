package com.maor.projectjr;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;

public class EmergencyService extends Service {

    public static final String EXTRA_NUMBERS = "numbers";
    private static final String CH_ID = "sos_calls";
    private static final int NOTIF_ID = 44;

    private ArrayList<String> numbers;
    private int index = 0;
    private boolean answered = false;
    private boolean dialing = false;

    private TelephonyManager telephonyManager;

    private final PhoneStateListener listener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:
                    update("Ringing " + current());
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    answered = true;
                    update("Connected to " + current() + " — stopping chain.");
                    stopSelf();
                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                    if (dialing && !answered) {
                        index++;
                        if (index < numbers.size()) {
                            update("No answer. Calling next: " + current());
                            placeCall(current());
                        } else {
                            update("No one answered. SOS chain finished.");
                            stopSelf();
                        }
                    }
                    break;
            }
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();

        Notification n = new NotificationCompat.Builder(this, CH_ID)
                .setContentTitle("Asthma SOS")
                .setContentText("Preparing emergency call chain…")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setOngoing(true)
                .build();

        // 👇 Use generic type (not phoneCall) to avoid new Android 14 restrictions
        startForeground(NOTIF_ID, n);

        // Check permission before listening
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED) {
            telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE);
        } else {
            update("Missing READ_PHONE_STATE permission — cannot monitor calls.");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        numbers = intent.getStringArrayListExtra(EXTRA_NUMBERS);
        if (numbers == null || numbers.isEmpty()) {
            update("No numbers to call. Stopping.");
            stopSelf();
            return START_NOT_STICKY;
        }
        index = 0;
        answered = false;
        update("Calling: " + current());
        placeCall(current());
        return START_STICKY;
    }

    private String current() { return numbers.get(index); }

    private void placeCall(String number) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            update("Missing CALL_PHONE permission.");
            stopSelf();
            return;
        }
        dialing = true;
        answered = false;
        Intent callIntent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number));
        callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(callIntent);
    }

    private void update(String msg) {
        Notification n = new NotificationCompat.Builder(this, CH_ID)
                .setContentTitle("Asthma SOS")
                .setContentText(msg)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setOngoing(true)
                .build();

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, n);

        Intent i = new Intent(MainActivity.ACTION_SOS_STATUS);
        i.putExtra(MainActivity.EXTRA_STATUS, msg);
        sendBroadcast(i);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (telephonyManager != null) {
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE);
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(NOTIF_ID);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CH_ID, "SOS Calls", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(ch);
        }
    }
}
