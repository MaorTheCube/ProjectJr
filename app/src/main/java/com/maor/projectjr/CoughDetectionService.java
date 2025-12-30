package com.maor.projectjr;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class CoughDetectionService extends Service {

    private static final String CHANNEL_ID = "cough_channel";
    private static final int NOTIF_ID = 99;

    public static final String ACTION_COUGH_STATUS = "cough_status_update";
    public static final String EXTRA_TEXT = "msg";

    private volatile boolean running = false;
    private AudioRecord recorder;

    private int coughCount = 0;
    private long lastCoughTime = 0;

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();

        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Asthma SOS")
                .setContentText("Listening for coughing…")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setOngoing(true)
                .build();
        startForeground(NOTIF_ID, n);

        running = true;
        new Thread(this::listenLoop).start();
    }

    private void sendStatus(String msg) {
        Intent i = new Intent(ACTION_COUGH_STATUS);
        i.putExtra(EXTRA_TEXT, msg);
        sendBroadcast(i);
    }

    private void listenLoop() {
        int sampleRate = 8000;
        int bufSize = AudioRecord.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) return;

        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize);

        short[] buffer = new short[bufSize];

        try {
            recorder.startRecording();

            while (running) {
                int read = recorder.read(buffer, 0, buffer.length);
                if (read <= 0) continue;

                double amp = 0;
                for (int i = 0; i < read; i++) {
                    amp += Math.abs(buffer[i]);
                }
                amp /= read;

                Log.d("COUGH_TEST", "amp=" + amp);
                sendStatus("Listening… (amp: " + ((int) amp) + ")");

                // Spike threshold (works on most phones)
                if (amp > 12000) {
                    long now = System.currentTimeMillis();

                    // Cooldown (2 sec)
                    if (now - lastCoughTime > 2000) {
                        lastCoughTime = now;
                        coughCount++;

                        Log.d("COUGH_TEST", "### SPIKE DETECTED — COUGH #" + coughCount);

                        sendStatus("COUGH DETECTED (#" + coughCount + ")");

                        onCoughDetected();
                    }
                }
            }
        } catch (Exception e) {
            Log.e("COUGH_TEST", "Error: ", e);
        } finally {
            if (recorder != null) {
                try { recorder.stop(); } catch (Exception ignored) {}
                recorder.release();
            }
        }
    }

    private void onCoughDetected() {
        SharedPreferences prefs = getSharedPreferences("AsthmaSOSPrefs", MODE_PRIVATE);
        String sickId = prefs.getString("sick_id", null);

        if (sickId != null) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            Map<String, Object> alert = new HashMap<>();
            alert.put("type", "COUGH");
            alert.put("time", Timestamp.now());
            db.collection("users").document(sickId)
                    .update("lastAlert", alert);
        }

        // SEND NOTIFICATION FOR TESTING
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Asthma SOS")
                .setContentText("COUGH DETECTED")
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setOngoing(true)
                .build();

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, n);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "Cough Detection",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(ch);
        }
    }
}
