package com.maor.projectjr;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

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
        LinearLayout sickIdSection = findViewById(R.id.section_sick_id);
        Button btnEditContacts = findViewById(R.id.btn_edit_contacts);
        Switch switchDefaultCough = findViewById(R.id.switch_default_cough_detection);
        TextView textSickId = findViewById(R.id.text_sick_id_settings);
        Button btnCopyId = findViewById(R.id.btn_copy_id);
        Button btnResetApp = findViewById(R.id.btn_reset_app);

        String role = prefs.getString(WelcomeActivity.KEY_ROLE, "sick");
        boolean isGuardian = "guardian".equals(role);

        if (isGuardian) {
            emergencyContactsSection.setVisibility(LinearLayout.GONE);
            coughDetectionSection.setVisibility(LinearLayout.GONE);
            sickIdSection.setVisibility(LinearLayout.GONE);
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

        // ✅ Safe reset: keep sick_id so one per device, avoid DB spam
        btnResetApp.setOnClickListener(v -> showResetDialog());
    }

    private void showResetDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Reset app?")
                .setMessage("This will clear your contacts and settings and return you to the first screen. Your ID will stay the same.")
                .setPositiveButton("Reset", (dialog, which) -> {

                    // Keep the same sick_id
                    String sickId = prefs.getString(MainActivity.KEY_SICK_ID, null);

                    // Clear most prefs (contacts/settings) but keep ID
                    prefs.edit()
                            .remove(MainActivity.KEY_DEFAULT_COUGH)
                            .remove("priority_numbers")
                            .remove("priority_labels")
                            .remove(SetupActivity.KEY_SICK_NAME)
                            .apply();

                    if (sickId != null) {
                        prefs.edit().putString(MainActivity.KEY_SICK_ID, sickId).apply();
                    }

                    // Clear guardian link if any
                    SharedPreferences guardianPrefs =
                            getSharedPreferences(GuardianSetupActivity.PREFS, MODE_PRIVATE);
                    guardianPrefs.edit().remove(GuardianSetupActivity.KEY_WATCHED_ID).apply();

                    // Go to WelcomeActivity fresh
                    Intent i = new Intent(this, WelcomeActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
