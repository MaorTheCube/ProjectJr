package com.maor.projectjr;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.DateFormat;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GuardianMainActivity extends AppCompatActivity {

    private static final String PREFS = "AsthmaSOSPrefs";
    private static final String KEY_WATCHED_ID = "guardian_watched_id";
    private static final String CHANNEL_ID = "guardian_alerts";

    private TextView sickNameText, lastAlertText, locationText;
    private FirebaseFirestore db;
    private DocumentReference watchedRef;
    private Timestamp lastAlertSeen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guardian_main);

        sickNameText = findViewById(R.id.sick_name_text);
        lastAlertText = findViewById(R.id.last_alert_text);
        locationText = findViewById(R.id.location_text);

        db = FirebaseFirestore.getInstance();
        createChannel();

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String watchedId = prefs.getString(KEY_WATCHED_ID, null);

        if (watchedId == null) {
            lastAlertText.setText("No linked sick ID. Go back and set it up.");
            return;
        }

        watchedRef = db.collection("users").document(watchedId);
        watchedRef.addSnapshotListener((doc, e) -> {
            if (e != null || doc == null || !doc.exists()) return;
            updateFromDocument(doc);
        });
    }

    private void updateFromDocument(DocumentSnapshot doc) {
        String name = doc.getString("name");
        sickNameText.setText("Sick: " + (name == null ? "-" : name));

        Map<String, Object> lastAlert = (Map<String, Object>) doc.get("lastAlert");
        if (lastAlert != null) {
            String type = (String) lastAlert.get("type");
            Timestamp time = (Timestamp) lastAlert.get("time");
            String timeText = (time != null)
                    ? DateFormat.getTimeInstance(DateFormat.SHORT).format(time.toDate())
                    : "-";

            lastAlertText.setText("Last alert: " + type + " at " + timeText);

            // If this is a new alert (time after lastAlertSeen) -> buzz + notification
            if (time != null && (lastAlertSeen == null || time.compareTo(lastAlertSeen) > 0)) {
                lastAlertSeen = time;
                showAlertNotification(type);
            }
        }

        Map<String, Object> loc = (Map<String, Object>) doc.get("lastLocation");
        if (loc != null) {
            Object lat = loc.get("lat");
            Object lng = loc.get("lng");
            String timeText = "";
            Timestamp locationTime = (Timestamp) loc.get("time");
            if (locationTime != null) {
                long diffMs = System.currentTimeMillis() - locationTime.toDate().getTime();
                long minutes = TimeUnit.MILLISECONDS.toMinutes(Math.max(0L, diffMs));
                timeText = minutes == 0 ? " (updated just now)" : " (updated " + minutes + " min ago)";
            }
            locationText.setText("Location: " + lat + ", " + lng + timeText);
        } else {
            locationText.setText("Location: -");
        }
    }

    private void showAlertNotification(String type) {
        // Vibrate
        Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vib != null && vib.hasVibrator()) {
            vib.vibrate(500);
        }

        // Sound
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.stat_sys_warning)
                        .setContentTitle("Asthma SOS alert")
                        .setContentText("New " + type + " alert from sick person")
                        .setAutoCancel(true)
                        .setSound(sound)
                        .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify((int) (System.currentTimeMillis() & 0xfffffff), builder.build());
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "Guardian Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager nm =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(ch);
        }
    }
}
