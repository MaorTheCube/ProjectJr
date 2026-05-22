package com.maor.projectjr;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.Vibrator;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GuardianAlertService extends Service {

    public static final String PREF_NOTIFY_SOS = "guardian_notify_sos";
    public static final String PREF_NOTIFY_COUGH = "guardian_notify_cough";
    public static final String PREF_BG_MONITORING = "guardian_background_monitoring";

    private static final String CHANNEL_MONITOR = "guardian_monitor";
    private static final String CHANNEL_ALERTS = "guardian_alert_channel";
    private static final int NOTIF_PERSISTENT = 77;

    private FirebaseFirestore db;
    private String guardianId;
    private ListenerRegistration rosterListener;
    private final Map<String, ListenerRegistration> patientListeners = new HashMap<>();
    private final Map<String, Timestamp> lastSeenAlerts = new HashMap<>();
    private final Map<String, String> patientNames = new HashMap<>();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
        db = FirebaseFirestore.getInstance();
        guardianId = resolveGuardianId();

        Notification persistent = new NotificationCompat.Builder(this, CHANNEL_MONITOR)
                .setContentTitle("Asthma SOS")
                .setContentText("Monitoring patients for alerts…")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        startForeground(NOTIF_PERSISTENT, persistent);

        listenToRoster();
    }

    private void listenToRoster() {
        if (guardianId == null || guardianId.isEmpty()) return;

        rosterListener = db.collection("guardians").document(guardianId)
                .collection("patients")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    Set<String> currentIds = new HashSet<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        String patientId = doc.getId();
                        currentIds.add(patientId);
                        String name = doc.getString("displayName");
                        if (name != null) patientNames.put(patientId, name);
                        if (!patientListeners.containsKey(patientId)) {
                            attachPatientListener(patientId);
                        }
                    }

                    Iterator<Map.Entry<String, ListenerRegistration>> it =
                            patientListeners.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<String, ListenerRegistration> entry = it.next();
                        if (!currentIds.contains(entry.getKey())) {
                            entry.getValue().remove();
                            it.remove();
                        }
                    }
                });
    }

    private void attachPatientListener(String patientId) {
        ListenerRegistration reg = db.collection("users").document(patientId)
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null || !doc.exists()) return;

                    String userName = doc.getString("name");
                    if (userName != null && !userName.isEmpty()) {
                        patientNames.putIfAbsent(patientId, userName);
                    }

                    @SuppressWarnings("unchecked")
                    Map<String, Object> lastAlert = (Map<String, Object>) doc.get("lastAlert");
                    if (lastAlert == null) return;

                    Timestamp time = (Timestamp) lastAlert.get("time");
                    String type = (String) lastAlert.get("type");
                    if (time == null || type == null) return;

                    Timestamp lastSeen = lastSeenAlerts.get(patientId);
                    if (lastSeen != null && time.compareTo(lastSeen) <= 0) return;

                    SharedPreferences prefs = getSharedPreferences(
                            GuardianSetupActivity.PREFS, MODE_PRIVATE);
                    boolean notifySos = prefs.getBoolean(PREF_NOTIFY_SOS, true);
                    boolean notifyCough = prefs.getBoolean(PREF_NOTIFY_COUGH, true);

                    boolean shouldNotify = ("SOS".equals(type) && notifySos)
                            || ("COUGH".equals(type) && notifyCough);

                    if (shouldNotify) {
                        lastSeenAlerts.put(patientId, time);
                        String displayName = patientNames.getOrDefault(patientId, patientId);
                        showAlertNotification(patientId, displayName, type);
                    }
                });
        patientListeners.put(patientId, reg);
    }

    private void showAlertNotification(String patientId, String patientName, String type) {
        Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vib != null && vib.hasVibrator()) vib.vibrate(600);

        Intent tapIntent = new Intent(this, GuardianMainActivity.class);
        tapIntent.putExtra(GuardianMainActivity.EXTRA_PATIENT_ID, patientId);
        tapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, patientId.hashCode(), tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = "SOS".equals(type)
                ? "Emergency SOS — " + patientName
                : "Cough detected — " + patientName;
        String body = "SOS".equals(type)
                ? patientName + " triggered an emergency. Tap to view location."
                : patientName + " had a cough event detected.";

        Notification n = new NotificationCompat.Builder(this, CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify((patientId + type).hashCode(), n);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (rosterListener != null) rosterListener.remove();
        for (ListenerRegistration lr : patientListeners.values()) lr.remove();
    }

    private String resolveGuardianId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) return user.getUid();
        SharedPreferences prefs = getSharedPreferences(GuardianSetupActivity.PREFS, MODE_PRIVATE);
        String id = prefs.getString("guardian_id", null);
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString("guardian_id", id).apply();
        }
        return id;
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_MONITOR, "Guardian Monitor", NotificationManager.IMPORTANCE_LOW));
            NotificationChannel alertCh = new NotificationChannel(
                    CHANNEL_ALERTS, "Guardian Alerts", NotificationManager.IMPORTANCE_HIGH);
            alertCh.enableVibration(true);
            nm.createNotificationChannel(alertCh);
        }
    }
}
