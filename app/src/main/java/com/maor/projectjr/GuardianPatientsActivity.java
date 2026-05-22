package com.maor.projectjr;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.navigation.NavigationView;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.firebase.Timestamp;
import android.content.SharedPreferences;

import java.util.UUID;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.Locale;

public class GuardianPatientsActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String guardianId;
    private final List<PatientRosterItem> items = new ArrayList<>();
    private final Map<String, ListenerRegistration> patientListeners = new HashMap<>();
    private ListenerRegistration rosterListener;
    private PatientAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guardian_patients);

        DrawerLayout drawerLayout = findViewById(R.id.guardian_drawer_layout);
        NavigationView navView = findViewById(R.id.guardian_navigation_view);

        MaterialToolbar toolbar = findViewById(R.id.guardian_patients_toolbar);
        toolbar.setNavigationOnClickListener(v ->
                drawerLayout.openDrawer(androidx.core.view.GravityCompat.START));

        navView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                drawerLayout.closeDrawers();
            } else if (id == R.id.nav_settings) {
                drawerLayout.closeDrawers();
                startActivity(new Intent(this, SettingsActivity.class));
            } else if (id == R.id.nav_about) {
                drawerLayout.closeDrawers();
                startActivity(new Intent(this, AboutActivity.class));
            }
            return true;
        });

        RecyclerView recyclerView = findViewById(R.id.patients_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PatientAdapter();
        recyclerView.setAdapter(adapter);

        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        guardianId = user != null ? user.getUid() : getOrCreateGuardianId();

        findViewById(R.id.btn_add_patient).setOnClickListener(v ->
                startActivity(new Intent(this, GuardianSetupActivity.class)));
    }

    @Override
    protected void onStart() {
        super.onStart();
        listenToRoster();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (rosterListener != null) rosterListener.remove();
        for (ListenerRegistration lr : patientListeners.values()) lr.remove();
        patientListeners.clear();
    }

    private void listenToRoster() {
        rosterListener = db.collection("guardians").document(guardianId)
                .collection("patients")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    Map<String, PatientRosterItem> merged = new HashMap<>();
                    for (PatientRosterItem item : items) merged.put(item.patientId, item);

                    List<String> currentIds = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        String patientId = doc.getId();
                        currentIds.add(patientId);
                        PatientRosterItem item = merged.get(patientId);
                        if (item == null) item = new PatientRosterItem(patientId);
                        item.displayName = doc.getString("displayName");
                        item.relationship = doc.getString("relationship");
                        merged.put(patientId, item);
                        attachPatientListener(item);
                    }

                    List<String> toRemove = new ArrayList<>();
                    for (String trackedId : patientListeners.keySet()) {
                        if (!currentIds.contains(trackedId)) toRemove.add(trackedId);
                    }
                    for (String removeId : toRemove) {
                        ListenerRegistration lr = patientListeners.remove(removeId);
                        if (lr != null) lr.remove();
                        merged.remove(removeId);
                    }

                    items.clear();
                    items.addAll(merged.values());
                    adapter.notifyDataSetChanged();
                    ((TextView) findViewById(R.id.empty_text)).setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    private void attachPatientListener(PatientRosterItem item) {
        if (patientListeners.containsKey(item.patientId)) return;
        ListenerRegistration registration = db.collection("users").document(item.patientId)
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null || !doc.exists()) return;
                    item.lastLocation = (Map<String, Object>) doc.get("lastLocation");
                    item.lastAlert = (Map<String, Object>) doc.get("lastAlert");
                    String userName = doc.getString("name");
                    if ((item.displayName == null || item.displayName.trim().isEmpty()) && userName != null) {
                        item.displayName = userName;
                    }
                    item.phone = doc.getString("phone");
                    adapter.notifyDataSetChanged();
                });
        patientListeners.put(item.patientId, registration);
    }

    private class PatientAdapter extends RecyclerView.Adapter<PatientAdapter.PatientVH> {

        @NonNull
        @Override
        public PatientVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_guardian_patient, parent, false);
            return new PatientVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull PatientVH h, int position) {
            PatientRosterItem item = items.get(position);
            h.name.setText(item.displayName != null ? item.displayName : item.patientId);
            h.meta.setText(buildMeta(item));
            h.status.setText(buildStatus(item));
            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(GuardianPatientsActivity.this, GuardianMainActivity.class);
                i.putExtra(GuardianMainActivity.EXTRA_PATIENT_ID, item.patientId);
                startActivity(i);
            });
            h.itemView.setOnLongClickListener(v -> {
                new AlertDialog.Builder(GuardianPatientsActivity.this)
                        .setTitle("Remove patient")
                        .setMessage("Unlink this patient from your roster?")
                        .setPositiveButton("Remove", (d, w) -> unlinkPatient(item.patientId))
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        class PatientVH extends RecyclerView.ViewHolder {
            TextView name, meta;
            Chip status;
            PatientVH(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.patient_name);
                meta = itemView.findViewById(R.id.patient_meta);
                status = itemView.findViewById(R.id.patient_status_chip);
            }
        }
    }

    private String buildStatus(PatientRosterItem item) {
        if (item.lastLocation == null) return "Offline";
        Timestamp ts = (Timestamp) item.lastLocation.get("time");
        if (ts == null) return "Offline";
        long ageMin = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - ts.toDate().getTime());
        String base = ageMin <= 5 ? "Online" : "Stale";
        return base + " • location " + Math.max(0, ageMin) + "m";
    }

    private String buildMeta(PatientRosterItem item) {
        List<String> details = new ArrayList<>();
        if (item.relationship != null && !item.relationship.trim().isEmpty()) {
            details.add(item.relationship.trim());
        }
        if (item.phone != null && !item.phone.trim().isEmpty()) {
            details.add(item.phone.trim());
        }
        if (item.lastAlert != null) {
            String type = asString(item.lastAlert.get("type"));
            Timestamp ts = (Timestamp) item.lastAlert.get("time");
            if (type != null) {
                if (ts != null) {
                    long ageMin = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - ts.toDate().getTime());
                    details.add(String.format(Locale.US, "Last alert: %s (%dm ago)", type, Math.max(0, ageMin)));
                } else {
                    details.add("Last alert: " + type);
                }
            }
        }
        if (item.lastLocation != null) {
            Object lat = item.lastLocation.get("lat");
            Object lng = item.lastLocation.get("lng");
            if (lat instanceof Number && lng instanceof Number) {
                details.add(String.format(Locale.US, "%.4f, %.4f", ((Number) lat).doubleValue(), ((Number) lng).doubleValue()));
            }
        }
        if (details.isEmpty()) return item.patientId;
        return android.text.TextUtils.join(" • ", details);
    }

    private String asString(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private void unlinkPatient(String patientId) {
        db.collection("guardians").document(guardianId)
                .collection("patients").document(patientId)
                .delete()
                .addOnSuccessListener(v -> Toast.makeText(this, "Patient removed", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to remove patient", Toast.LENGTH_SHORT).show());
    }

    private static class PatientRosterItem {
        String patientId;
        String displayName;
        String relationship;
        String phone;
        Map<String, Object> lastLocation;
        Map<String, Object> lastAlert;

        PatientRosterItem(String patientId) { this.patientId = patientId; }
    }
    private String getOrCreateGuardianId() {
        SharedPreferences prefs = getSharedPreferences(GuardianSetupActivity.PREFS, MODE_PRIVATE);
        String guardianId = prefs.getString("guardian_id", null);
        if (guardianId == null || guardianId.trim().isEmpty()) {
            guardianId = UUID.randomUUID().toString();
            prefs.edit().putString("guardian_id", guardianId).apply();
        }
        return guardianId;
    }

}
