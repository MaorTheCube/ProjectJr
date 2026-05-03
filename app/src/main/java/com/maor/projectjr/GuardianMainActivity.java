package com.maor.projectjr;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.os.Vibrator;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.app.NotificationCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.DateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GuardianMainActivity extends AppCompatActivity {

    private static final String PREFS = "AsthmaSOSPrefs";
    private static final String KEY_WATCHED_ID = "guardian_watched_id";
    private static final String CHANNEL_ID = "guardian_alerts";
    private static final String TAG = "GuardianMainActivity";

    private TextView sickNameText, lastAlertText, locationTimeText, locationCoordsText, locationAddressText;
    private View locationMapContainer;
    private FirebaseFirestore db;
    private DocumentReference watchedRef;
    private Timestamp lastAlertSeen;
    private double lastLat = Double.NaN;
    private double lastLng = Double.NaN;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                // Guardian location view can still work without local permission because coordinates come from Firestore.
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guardian_main);

        sickNameText = findViewById(R.id.sick_name_text);
        lastAlertText = findViewById(R.id.last_alert_text);
        locationTimeText = findViewById(R.id.location_time);
        locationCoordsText = findViewById(R.id.location_coords);
        locationAddressText = findViewById(R.id.location_address);
        locationMapContainer = findViewById(R.id.location_map_container);
        locationMapContainer.setOnClickListener(v -> openLastLocationInMaps());
        locationAddressText.setOnClickListener(v -> openLastLocationInMaps());
        locationMapContainer.setContentDescription("Open live location in map");

        db = FirebaseFirestore.getInstance();
        createChannel();
        requestLocationPermissionIfNeeded();

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
            String timeText = "Updated recently";
            Timestamp locationTime = (Timestamp) loc.get("time");
            if (locationTime != null) {
                long diffMs = System.currentTimeMillis() - locationTime.toDate().getTime();
                long minutes = TimeUnit.MILLISECONDS.toMinutes(Math.max(0L, diffMs));
                timeText = minutes == 0 ? "Updated just now" : "Updated " + minutes + " min ago";
            }
            if (lat instanceof Number && lng instanceof Number) {
                lastLat = ((Number) lat).doubleValue();
                lastLng = ((Number) lng).doubleValue();
                String coords = String.format(
                        Locale.getDefault(),
                        "Lat %.5f, Lng %.5f",
                        lastLat,
                        lastLng
                );
                locationCoordsText.setText(coords);
                locationTimeText.setText(timeText);
                reverseGeocode(lastLat, lastLng);
            } else {
                locationCoordsText.setText("Lat -, Lng -");
                locationTimeText.setText("Location unavailable");
                locationAddressText.setText("Address unavailable");
            }
        } else {
            locationCoordsText.setText("Lat -, Lng -");
            locationTimeText.setText("Location offline");
            locationAddressText.setText("Address unavailable");
        }
    }

    private void requestLocationPermissionIfNeeded() {
        boolean fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (!fineGranted && !coarseGranted) {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void reverseGeocode(double lat, double lng) {
        if (!Geocoder.isPresent()) {
            locationAddressText.setText("Address unavailable");
            return;
        }

        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        geocoder.getFromLocation(lat, lng, 1, new Geocoder.GeocodeListener() {
            @Override
            public void onGeocode(@NonNull List<Address> addresses) {
                if (addresses.isEmpty()) {
                    locationAddressText.setText("Address unavailable");
                    return;
                }
                String addressLine = addresses.get(0).getAddressLine(0);
                if (addressLine == null || addressLine.trim().isEmpty()) {
                    locationAddressText.setText("Address unavailable");
                } else {
                    locationAddressText.setText(addressLine);
                }
            }

            @Override
            public void onError(String errorMessage) {
                Log.w(TAG, "Reverse geocoding failed: " + errorMessage);
                locationAddressText.setText("Address unavailable");
            }
        });
    }

    private void openLastLocationInMaps() {
        if (Double.isNaN(lastLat) || Double.isNaN(lastLng)) {
            Toast.makeText(this, "No location available yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri googleMapsUri = Uri.parse("google.navigation:q=" + lastLat + "," + lastLng + "&mode=d");
        Intent googleMapsIntent = new Intent(Intent.ACTION_VIEW, googleMapsUri);
        googleMapsIntent.setPackage("com.google.android.apps.maps");

        if (googleMapsIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(googleMapsIntent);
            return;
        }

        Uri webMapsUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + lastLat + "," + lastLng);
        Intent webIntent = new Intent(Intent.ACTION_VIEW, webMapsUri);
        if (webIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(webIntent);
        } else {
            Toast.makeText(this, "No map app available to open location.", Toast.LENGTH_SHORT).show();
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
