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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class CoughDetectionService extends Service {

    private static final String CHANNEL_ID = "cough_channel";
    private static final int NOTIF_ID = 99;

    public static final String ACTION_COUGH_STATUS = "cough_status_update";
    public static final String EXTRA_TEXT = "msg";

    // 16 kHz gives better frequency coverage for cough sounds (300 Hz–3 kHz range)
    private static final int SAMPLE_RATE = 16000;
    // ~20 ms window per energy measurement
    private static final int BLOCK_SIZE = 320;
    // Energy spike threshold (RMS). Higher than before because 16 kHz captures more signal.
    private static final double SPIKE_THRESHOLD = 8000;
    // A cough is at least 2 spikes within this window (ms)
    private static final long COUGH_WINDOW_MS = 600;
    // Minimum gap between two counted spikes (ms) — avoids counting one sustained burst as many
    private static final long MIN_SPIKE_GAP_MS = 40;
    // Cooldown between cough events (ms)
    private static final long COOLDOWN_MS = 3000;

    private volatile boolean running = false;
    private AudioRecord recorder;
    private int coughCount = 0;
    private long lastCoughTime = 0;

    @Nullable
    @Override
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
        int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        // Use at least 4× the block size so we never starve the read
        int bufSize = Math.max(minBuf, BLOCK_SIZE * 4);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) return;

        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize);

        short[] buffer = new short[BLOCK_SIZE];

        // Sliding window of recent spike timestamps within COUGH_WINDOW_MS
        Deque<Long> recentSpikes = new ArrayDeque<>();
        long lastSpikeTime = 0;

        try {
            recorder.startRecording();

            while (running) {
                int read = recorder.read(buffer, 0, BLOCK_SIZE);
                if (read <= 0) continue;

                double rms = computeRms(buffer, read);
                long now = System.currentTimeMillis();

                if (rms > SPIKE_THRESHOLD) {
                    // Only count as a new spike if enough time has passed since the last one
                    if (now - lastSpikeTime >= MIN_SPIKE_GAP_MS) {
                        lastSpikeTime = now;
                        recentSpikes.addLast(now);
                    }
                }

                // Evict spikes older than COUGH_WINDOW_MS
                while (!recentSpikes.isEmpty()
                        && now - recentSpikes.peekFirst() > COUGH_WINDOW_MS) {
                    recentSpikes.pollFirst();
                }

                // A cough = at least 2 distinct spikes in the window, and cooldown elapsed
                if (recentSpikes.size() >= 2 && now - lastCoughTime > COOLDOWN_MS) {
                    lastCoughTime = now;
                    coughCount++;
                    recentSpikes.clear();

                    Log.d("COUGH", "Cough #" + coughCount + " detected (pattern match)");
                    sendStatus("COUGH DETECTED (#" + coughCount + ")");
                    onCoughDetected();
                } else {
                    sendStatus("Listening… (rms: " + (int) rms + ")");
                }
            }
        } catch (Exception e) {
            Log.e("COUGH", "Error in listen loop", e);
        } finally {
            if (recorder != null) {
                try { recorder.stop(); } catch (Exception ignored) {}
                recorder.release();
            }
        }
    }

    private double computeRms(short[] buffer, int count) {
        double sum = 0;
        for (int i = 0; i < count; i++) {
            sum += (double) buffer[i] * buffer[i];
        }
        return Math.sqrt(sum / count);
    }

    private void onCoughDetected() {
        SharedPreferences prefs = getSharedPreferences("AsthmaSOSPrefs", MODE_PRIVATE);
        String sickId = prefs.getString("sick_id", null);

        if (sickId != null) {
            Map<String, Object> alert = new HashMap<>();
            alert.put("type", "COUGH");
            alert.put("time", Timestamp.now());
            FirebaseFirestore.getInstance()
                    .collection("users").document(sickId)
                    .update("lastAlert", alert);
        }

        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Asthma SOS")
                .setContentText("Cough detected (#" + coughCount + ")")
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setOngoing(false)
                .setAutoCancel(true)
                .build();

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID + coughCount, n);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Cough Detection", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(ch);
        }
    }
}
