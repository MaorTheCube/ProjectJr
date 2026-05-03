package com.maor.projectjr;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class GuardianSetupActivity extends AppCompatActivity {


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
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    String guardianId = user != null ? user.getUid() : "guardian_local";
                    Map<String, Object> rosterEntry = new HashMap<>();
                    rosterEntry.put("patientId", id);
                    rosterEntry.put("displayName", doc.getString("name"));
                    db.collection("guardians").document(guardianId)
                            .collection("patients").document(id)
                            .set(rosterEntry)
                            .addOnSuccessListener(unused -> {
                                status.setText("Patient added. Opening roster…");
                                startActivity(new Intent(this, GuardianPatientsActivity.class));
                                finish();
                            })
                            .addOnFailureListener(err -> status.setText("Failed to add patient."));
                } else {
                    status.setText("No user found with that ID.");
                }
            });
        });
    }
}
