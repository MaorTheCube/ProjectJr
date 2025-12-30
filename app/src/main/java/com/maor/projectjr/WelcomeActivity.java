package com.maor.projectjr;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class WelcomeActivity extends AppCompatActivity {

    public static final String PREFS = "AsthmaSOSPrefs";
    public static final String KEY_ROLE = "user_role";   // "sick" or "guardian"
    public static final String KEY_FIRST_LAUNCH = "firstLaunch";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean firstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true);
        String role = prefs.getString(KEY_ROLE, null);

        // If not first launch, skip welcome and route by role
        if (!firstLaunch && role != null) {
            if ("sick".equals(role)) {
                startActivity(new Intent(this, MainActivity.class));
            } else {
                startActivity(new Intent(this, GuardianMainActivity.class));
            }
            finish();
            return;
        }

        setContentView(R.layout.activity_welcome);

        TextView title = findViewById(R.id.welcome_title);
        TextView subtitle = findViewById(R.id.welcome_subtitle);
        Button btnSick = findViewById(R.id.btn_sick);
        Button btnGuardian = findViewById(R.id.btn_guardian);

        // Fade in animation
        title.animate().alpha(1f).setDuration(1200).withEndAction(() -> {
            subtitle.animate().alpha(1f).setDuration(800).start();
            btnSick.animate().alpha(1f).setDuration(800).start();
            btnGuardian.animate().alpha(1f).setDuration(800).start();
        }).start();

        btnSick.setOnClickListener(v -> {
            prefs.edit()
                    .putString(KEY_ROLE, "sick")
                    .putBoolean(KEY_FIRST_LAUNCH, false)
                    .apply();
            startActivity(new Intent(this, SetupActivity.class)); // your sick setup
            finish();
        });

        btnGuardian.setOnClickListener(v -> {
            prefs.edit()
                    .putString(KEY_ROLE, "guardian")
                    .putBoolean(KEY_FIRST_LAUNCH, false)
                    .apply();
            startActivity(new Intent(this, GuardianSetupActivity.class));
            finish();
        });
    }
}
