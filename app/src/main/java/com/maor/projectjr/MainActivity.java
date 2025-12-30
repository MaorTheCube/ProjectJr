package com.maor.projectjr;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    public static final String PREFS = "AsthmaSOSPrefs";
    public static final String KEY_SICK_ID = "sick_id";
    public static final String KEY_DEFAULT_COUGH = "default_cough_detection";

    private Button emergencyButton;
    private TextView statusText, idText;
    private Switch coughSwitch;
    private SharedPreferences prefs;

    public static final String ACTION_SOS_STATUS = "com.maor.projectjr.SOS_STATUS";
    public static final String EXTRA_STATUS = "status";

    private final BroadcastReceiver coughReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String txt = intent.getStringExtra(CoughDetectionService.EXTRA_TEXT);
            if (txt != null) statusText.setText(txt);
        }
    };

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (ACTION_SOS_STATUS.equals(intent.getAction())) {
                String msg = intent.getStringExtra(EXTRA_STATUS);
                if (msg != null) statusText.setText(msg);
            }
        }
    };

    private static final int REQUEST_CALL_PERMISSION = 101;
    private static final int REQUEST_AUDIO_PERMISSION = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        // Drawer + toolbar
        DrawerLayout drawerLayout = findViewById(R.id.drawerLayout);
        NavigationView navView = findViewById(R.id.navigationView);
        MaterialToolbar topAppBar = findViewById(R.id.topAppBar);

        topAppBar.setNavigationOnClickListener(v ->
                drawerLayout.openDrawer(androidx.core.view.GravityCompat.START));

        navView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                drawerLayout.closeDrawers();
                return true;
            } else if (id == R.id.nav_settings) {
                drawerLayout.closeDrawers();
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            } else if (id == R.id.nav_about) {
                drawerLayout.closeDrawers();
                startActivity(new Intent(this, AboutActivity.class));
                return true;
            }
            return false;
        });

        emergencyButton = findViewById(R.id.emergency_button);
        statusText = findViewById(R.id.status_text);
        idText = findViewById(R.id.sick_id_text);
        coughSwitch = findViewById(R.id.switch_cough);

        String sickId = prefs.getString(KEY_SICK_ID, "--");
        idText.setText("ID: " + sickId);

        emergencyButton.setOnClickListener(v -> triggerEmergency());

        // Load default cough detection setting
        boolean defaultCough = prefs.getBoolean(KEY_DEFAULT_COUGH, false);
        coughSwitch.setChecked(defaultCough);
        if (defaultCough) {
            startCoughDetection();
        }

        coughSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                startCoughDetection();
            } else {
                stopCoughDetection();
            }
        });

        // Permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CALL_PHONE}, REQUEST_CALL_PERMISSION);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        registerReceiver(coughReceiver,
                new IntentFilter(CoughDetectionService.ACTION_COUGH_STATUS),
                Context.RECEIVER_NOT_EXPORTED);
        registerReceiver(statusReceiver,
                new IntentFilter(ACTION_SOS_STATUS),
                Context.RECEIVER_NOT_EXPORTED);
    }

    @Override protected void onPause() {
        super.onPause();
        unregisterReceiver(coughReceiver);
        unregisterReceiver(statusReceiver);
    }

    private void startCoughDetection() {
        Intent i = new Intent(this, CoughDetectionService.class);
        startForegroundService(i);
        statusText.setText("Cough detection enabled.");
    }

    private void stopCoughDetection() {
        Intent i = new Intent(this, CoughDetectionService.class);
        stopService(i);
        statusText.setText("");
    }

    private void triggerEmergency() {
        Set<String> nums = prefs.getStringSet("priority_numbers", new HashSet<>());
        if (nums == null || nums.isEmpty()) {
            statusText.setText("No emergency numbers configured.");
            return;
        }
        ArrayList<String> ordered = new ArrayList<>(nums);
        Intent svc = new Intent(this, EmergencyService.class);
        svc.putStringArrayListExtra(EmergencyService.EXTRA_NUMBERS, ordered);
        startForegroundService(svc);
        statusText.setText("Starting emergency call chain…");
    }
}
