package com.maor.projectjr;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class GuardianSetupActivity extends AppCompatActivity {

    public static final String PREFS = "AsthmaSOSPrefs";
    public static final String KEY_WATCHED_ID = "guardian_watched_id";

    private EditText inputId;
    private TextView status;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guardian_setup);

        inputId = findViewById(R.id.input_sick_id);
        status = findViewById(R.id.guardian_status);
        Button btn = findViewById(R.id.btn_link);

        db = FirebaseFirestore.getInstance();

        btn.setOnClickListener(v -> {
            String id = inputId.getText().toString().trim();
            if (id.isEmpty()) {
                status.setText("Enter the ID shown on the sick person's app.");
                return;
            }

            status.setText("Checking ID…");

            db.collection("users").document(id).get().addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    status.setText("Error connecting to server.");
                    return;
                }
                DocumentSnapshot doc = task.getResult();
                if (doc != null && doc.exists()) {
                    SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
                    prefs.edit().putString(KEY_WATCHED_ID, id).apply();
                    status.setText("Linked! Opening guardian view…");
                    startActivity(new Intent(this, GuardianMainActivity.class));
                    finish();
                } else {
                    status.setText("No user found with that ID.");
                }
            });
        });
    }
}
