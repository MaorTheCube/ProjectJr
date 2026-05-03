package com.maor.projectjr;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class LocationSharingService extends Service {

    public static final String ACTION_START_PERSISTENT = "com.maor.projectjr.action.START_PERSISTENT";
    public static final String ACTION_START_EMERGENCY = "com.maor.projectjr.action.START_EMERGENCY";
    public static final String ACTION_STOP = "com.maor.projectjr.action.STOP";
    public static final String PREF_ALWAYS_SHARE_LOCATION = "always_share_location_bg";

    private static final String CHANNEL_ID = "location_sharing";
    private static final int NOTIF_ID = 121;
    private static final long UPDATE_INTERVAL_EMERGENCY_MS = 15000L;
    private static final long UPDATE_INTERVAL_NORMAL_MS = 60_000L * 3L;

    private static final String MODE_NORMAL = "normal";
    private static final String MODE_EMERGENCY = "emergency";

    private FusedLocationProviderClient locationClient;
    private LocationCallback locationCallback;
    private FirebaseFirestore db;
    private String sickId;
    private String currentMode = MODE_NORMAL;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();

        startForeground(NOTIF_ID, buildNotification(MODE_NORMAL));

        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        sickId = prefs.getString(MainActivity.KEY_SICK_ID, null);

        locationClient = LocationServices.getFusedLocationProviderClient(this);
        db = FirebaseFirestore.getInstance();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (sickId == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_START_EMERGENCY.equals(action)) {
            currentMode = MODE_EMERGENCY;
        } else {
            currentMode = MODE_NORMAL;
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, buildNotification(currentMode));

        if (!hasLocationPermission()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startLocationUpdates();
        return START_STICKY;
    }

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startLocationUpdates() {
        if (locationCallback != null) {
            locationClient.removeLocationUpdates(locationCallback);
            locationCallback = null;
        }

        long interval = MODE_EMERGENCY.equals(currentMode)
                ? UPDATE_INTERVAL_EMERGENCY_MS
                : UPDATE_INTERVAL_NORMAL_MS;
        int priority = MODE_EMERGENCY.equals(currentMode)
                ? Priority.PRIORITY_HIGH_ACCURACY
                : Priority.PRIORITY_BALANCED_POWER_ACCURACY;

        LocationRequest request = new LocationRequest.Builder(priority, interval)
                .setMinUpdateIntervalMillis(interval)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null) return;
                Location loc = result.getLastLocation();
                if (loc != null) {
                    pushLocation(loc);
                }
            }
        };

        if (!hasLocationPermission()) return;
        locationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
    }

    private void pushLocation(Location loc) {
        Map<String, Object> lastLocation = new HashMap<>();
        lastLocation.put("lat", loc.getLatitude());
        lastLocation.put("lng", loc.getLongitude());
        lastLocation.put("time", Timestamp.now());
        lastLocation.put("accuracy", loc.getAccuracy());

        Map<String, Object> updates = new HashMap<>();
        updates.put("lastLocation", lastLocation);
        updates.put("locationTrackingMode", currentMode);

        db.collection("users")
                .document(sickId)
                .update(updates);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (locationCallback != null) {
            locationClient.removeLocationUpdates(locationCallback);
            locationCallback = null;
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(NOTIF_ID);
    }

    private Notification buildNotification(String mode) {
        String modeText = MODE_EMERGENCY.equals(mode) ? "Emergency mode" : "Normal mode";
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Asthma SOS")
                .setContentText("Sharing location in " + modeText)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setOngoing(true)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "Location Sharing",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(ch);
        }
    }
}
