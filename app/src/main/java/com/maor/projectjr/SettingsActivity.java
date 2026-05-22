package com.maor.projectjr;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.HashSet;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // ✅ Toolbar back arrow (works even with NoActionBar theme)
        MaterialToolbar toolbar = findViewById(R.id.settings_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);

        LinearLayout emergencyContactsSection = findViewById(R.id.section_emergency_contacts);
        LinearLayout coughDetectionSection = findViewById(R.id.section_cough_detection);
        LinearLayout bgLocationSection = findViewById(R.id.section_background_location);
        LinearLayout sickIdSection = findViewById(R.id.section_sick_id);
        Button btnEditContacts = findViewById(R.id.btn_edit_contacts);
        Switch switchDefaultCough = findViewById(R.id.switch_default_cough_detection);
        Switch switchBackgroundLocation = findViewById(R.id.switch_background_location);
        TextView textSickId = findViewById(R.id.text_sick_id_settings);
        Button btnCopyId = findViewById(R.id.btn_copy_id);
        Button btnResetApp = findViewById(R.id.btn_reset_app);

        String role = prefs.getString(WelcomeActivity.KEY_ROLE, "sick");
        boolean isGuardian = "guardian".equals(role);

        if (isGuardian) {
            emergencyContactsSection.setVisibility(LinearLayout.GONE);
            coughDetectionSection.setVisibility(LinearLayout.GONE);
            bgLocationSection.setVisibility(LinearLayout.GONE);
            sickIdSection.setVisibility(LinearLayout.GONE);

            // Show guardian cards
            findViewById(R.id.card_guardian_sos).setVisibility(View.VISIBLE);
            findViewById(R.id.card_guardian_cough).setVisibility(View.VISIBLE);
            findViewById(R.id.card_guardian_bg).setVisibility(View.VISIBLE);
            findViewById(R.id.card_guardian_patients).setVisibility(View.VISIBLE);

            SharedPreferences guardianPrefs = getSharedPreferences(GuardianSetupActivity.PREFS, MODE_PRIVATE);

            Switch switchSos = findViewById(R.id.switch_guardian_notify_sos);
            Switch switchCough = findViewById(R.id.switch_guardian_notify_cough);
            Switch switchBg = findViewById(R.id.switch_guardian_background);
            Button btnManagePatients = findViewById(R.id.btn_manage_patients);

            switchSos.setChecked(guardianPrefs.getBoolean(GuardianAlertService.PREF_NOTIFY_SOS, true));
            switchCough.setChecked(guardianPrefs.getBoolean(GuardianAlertService.PREF_NOTIFY_COUGH, true));
            switchBg.setChecked(guardianPrefs.getBoolean(GuardianAlertService.PREF_BG_MONITORING, false));

            switchSos.setOnCheckedChangeListener((b, checked) ->
                    guardianPrefs.edit().putBoolean(GuardianAlertService.PREF_NOTIFY_SOS, checked).apply());

            switchCough.setOnCheckedChangeListener((b, checked) ->
                    guardianPrefs.edit().putBoolean(GuardianAlertService.PREF_NOTIFY_COUGH, checked).apply());

            switchBg.setOnCheckedChangeListener((b, checked) -> {
                guardianPrefs.edit().putBoolean(GuardianAlertService.PREF_BG_MONITORING, checked).apply();
                Intent svc = new Intent(this, GuardianAlertService.class);
                if (checked) {
                    startForegroundService(svc);
                } else {
                    stopService(svc);
                }
                Toast.makeText(this,
                        checked ? "Background monitoring started." : "Background monitoring stopped.",
                        Toast.LENGTH_SHORT).show();
            });

            btnManagePatients.setOnClickListener(v ->
                    startActivity(new Intent(this, GuardianPatientsActivity.class)));
        } else {
            String sickId = prefs.getString(MainActivity.KEY_SICK_ID, "--");
            textSickId.setText("ID: " + sickId);

            boolean defaultCough = prefs.getBoolean(MainActivity.KEY_DEFAULT_COUGH, false);
            switchDefaultCough.setChecked(defaultCough);

            switchDefaultCough.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean(MainActivity.KEY_DEFAULT_COUGH, isChecked).apply();
                Toast.makeText(this,
                        isChecked ? "Cough detection will start automatically." :
                                "Cough detection will not auto-start.",
                        Toast.LENGTH_SHORT).show();
            });

            boolean alwaysShare = prefs.getBoolean(LocationSharingService.PREF_ALWAYS_SHARE_LOCATION, false);
            switchBackgroundLocation.setChecked(alwaysShare);
            switchBackgroundLocation.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked && !hasBackgroundLocationPermission()) {
                    switchBackgroundLocation.setChecked(false);
                    Toast.makeText(this,
                            "Background location is required. Grant 'Allow all the time' in settings.",
                            Toast.LENGTH_LONG).show();
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.setData(android.net.Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                    return;
                }

                prefs.edit().putBoolean(LocationSharingService.PREF_ALWAYS_SHARE_LOCATION, isChecked).apply();
                Intent svc = new Intent(this, LocationSharingService.class);
                svc.setAction(isChecked
                        ? LocationSharingService.ACTION_START_PERSISTENT
                        : LocationSharingService.ACTION_STOP);
                if (isChecked) {
                    startForegroundService(svc);
                } else {
                    startService(svc);
                }
            });

            btnEditContacts.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));

            btnCopyId.setOnClickListener(v -> {
                if (sickId == null || sickId.equals("--")) {
                    Toast.makeText(this, "No ID available yet.", Toast.LENGTH_SHORT).show();
                    return;
                }
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("Asthma SOS ID", sickId));
                    Toast.makeText(this, "ID copied to clipboard.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // ✅ Hard reset: wipe prefs and return to role selection
        btnResetApp.setOnClickListener(v -> showResetDialog());
    }

    private boolean hasBackgroundLocationPermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            return true;
        }
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void showResetDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Reset app?")
                .setMessage("This will clear all saved data and return you to the role selection screen.")
                .setPositiveButton("Reset", (dialog, which) -> {
                    prefs.edit().clear().apply();

                    SharedPreferences guardianPrefs =
                            getSharedPreferences(GuardianSetupActivity.PREFS, MODE_PRIVATE);
                    guardianPrefs.edit().clear().apply();

                    // Go to WelcomeActivity fresh
                    Intent i = new Intent(this, WelcomeActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
