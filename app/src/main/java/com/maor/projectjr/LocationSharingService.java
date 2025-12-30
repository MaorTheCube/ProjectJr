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
import android.os.Handler;
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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class LocationSharingService extends Service {

    public static final String EXTRA_DURATION_MS = "duration_ms";

    private static final String CHANNEL_ID = "location_sharing";
    private static final int NOTIF_ID = 121;
    private static final long DEFAULT_DURATION_MS = 30 * 60 * 1000L;
    private static final long UPDATE_INTERVAL_MS = 15000L;

    private FusedLocationProviderClient locationClient;
    private LocationCallback locationCallback;
    private FirebaseFirestore db;
    private String sickId;
    private long sharingUntilMs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();

        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Asthma SOS")
                .setContentText("Sharing location for emergency...")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setOngoing(true)
                .build();
        startForeground(NOTIF_ID, n);

        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        sickId = prefs.getString(MainActivity.KEY_SICK_ID, null);

        locationClient = LocationServices.getFusedLocationProviderClient(this);
        db = FirebaseFirestore.getInstance();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (sickId == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        long durationMs = DEFAULT_DURATION_MS;
        if (intent != null) {
            durationMs = intent.getLongExtra(EXTRA_DURATION_MS, DEFAULT_DURATION_MS);
        }
        if (sharingUntilMs == 0L) {
            sharingUntilMs = System.currentTimeMillis() + durationMs;
            updateSharingUntil(sharingUntilMs, false);
        }

        if (!hasLocationPermission()) {
            updateSharingUntil(System.currentTimeMillis(), true);
            stopSelf();
            return START_NOT_STICKY;
        }

        startLocationUpdates();
        scheduleStop();
        return START_STICKY;
    }

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startLocationUpdates() {
        if (locationCallback != null) return;

        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                UPDATE_INTERVAL_MS
        ).setMinUpdateIntervalMillis(UPDATE_INTERVAL_MS).build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null) return;
                if (System.currentTimeMillis() > sharingUntilMs) {
                    stopSelf();
                    return;
                }
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

        Map<String, Object> updates = new HashMap<>();
        updates.put("lastLocation", lastLocation);
        updates.put("locationSharingUntil", new Timestamp(new Date(sharingUntilMs)));
        updates.put("lastLocationExpired", false);

        db.collection("users")
                .document(sickId)
                .update(updates);
    }

    private void updateSharingUntil(long untilMs, boolean expired) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("locationSharingUntil", new Timestamp(new Date(untilMs)));
        updates.put("lastLocationExpired", expired);
        db.collection("users")
                .document(sickId)
                .update(updates);
    }

    private void scheduleStop() {
        handler.removeCallbacksAndMessages(null);
        long delay = Math.max(0L, sharingUntilMs - System.currentTimeMillis());
        handler.postDelayed(this::stopSelf, delay);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (locationCallback != null) {
            locationClient.removeLocationUpdates(locationCallback);
            locationCallback = null;
        }
        handler.removeCallbacksAndMessages(null);
        if (sickId != null) {
            updateSharingUntil(System.currentTimeMillis(), true);
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(NOTIF_ID);
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
