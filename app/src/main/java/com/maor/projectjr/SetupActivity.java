package com.maor.projectjr;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.TextView;
import android.telephony.TelephonyManager;


import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.hbb20.CountryCodePicker;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SetupActivity extends AppCompatActivity {
    public static final String KEY_SICK_ID = "sick_id";
    public static final String KEY_SICK_NAME = "sick_name";
    private TextInputEditText nameInput;
    private RecyclerView list;
    private ContactsAdapter adapter;
    private CountryCodePicker countryPicker;
    private TextInputEditText phoneInput;
    private TextView status;
    private Button addBtn, continueBtn;

    private final List<String> numbers = new ArrayList<>();
    private SharedPreferences prefs;

    private final ActivityResultLauncher<String> requestCallPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (!granted)
                            status.setText("Call permission not granted (you can enable it later in Settings).");
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        prefs = getSharedPreferences("AsthmaSOSPrefs", MODE_PRIVATE);

        // Initialize UI
        status = findViewById(R.id.setup_status);
        countryPicker = findViewById(R.id.country_code_picker);
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null && tm.getSimCountryIso() != null) {
            countryPicker.setCountryForNameCode(tm.getSimCountryIso().toUpperCase());
        } else {
            countryPicker.setAutoDetectedCountry(true);
        }
        nameInput = findViewById(R.id.contact_name_input);
        phoneInput = findViewById(R.id.phone_number_input);
        addBtn = findViewById(R.id.add_number_btn);
        continueBtn = findViewById(R.id.continue_btn);
        list = findViewById(R.id.contacts_recycler);

        // Setup RecyclerView
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ContactsAdapter(numbers, this::removeAt);
        list.setAdapter(adapter);

        // Enable drag-and-drop
        ItemTouchHelper helper = new ItemTouchHelper(
                new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
                    @Override
                    public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                                          @NonNull RecyclerView.ViewHolder tgt) {
                        int from = vh.getBindingAdapterPosition(), to = tgt.getBindingAdapterPosition();
                        String moved = numbers.remove(from);
                        numbers.add(to, moved);
                        adapter.notifyItemMoved(from, to);
                        return true;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) { }

                    @Override
                    public boolean isLongPressDragEnabled() { return true; }
                });
        helper.attachToRecyclerView(list);

        // Restore saved numbers
        Set<String> saved = prefs.getStringSet("priority_numbers", null);
        if (saved != null) {
            numbers.addAll(saved);
            adapter.notifyDataSetChanged();
        }

        // Add number
        addBtn.setOnClickListener(v -> addNumber());
        continueBtn.setOnClickListener(v -> saveAndContinue());

        // Ask permission early
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            requestCallPermission.launch(Manifest.permission.CALL_PHONE);
        }
    }
    private String cleanNumber(String rawCode, String rawPhone) {
        String code = rawCode.startsWith("+") ? rawCode : "+" + rawCode;
        String phone = rawPhone.replaceAll("[^0-9]", "");

        // Combine temporarily
        String full = code + phone;

        // ✅ Detect cases where there’s an extra 0 after country code
        // e.g. +9720XXXXXXXX or +97200XXXXXXXX
        if (full.matches("^\\+9720+\\d{7,}$")) {
            full = full.replaceFirst("\\+9720+", "+972");
        }

        // ✅ Handle local-only case (e.g. 0501234567 → +972501234567)
        else if (phone.startsWith("0") && phone.length() == 10) {
            full = code + phone.substring(1);
        }

        // ✅ Handle correctly formatted numbers (no change)
        else if (phone.length() >= 9 && phone.length() <= 12 && phone.startsWith("5")) {
            full = code + phone;
        }

        return full;
    }
    private void addNumber() {
        String name = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";
        String code = "+" + countryPicker.getSelectedCountryCode();
        String phone = phoneInput.getText() != null ? phoneInput.getText().toString().trim() : "";

        String full = cleanNumber(code, phone);


        if (TextUtils.isEmpty(phone)) {
            status.setText("Enter a phone number.");
            return;
        }
        if (!Patterns.PHONE.matcher(full).matches() || full.length() < 7) {
            status.setText("Invalid phone number format.");
            return;
        }

        // Prevent dupes by number (even if different name)
        for (String s : numbers) {
            String existing = s.contains(" – ") ? s.substring(s.indexOf(" – ") + 3).trim() : s.trim();
            if (existing.equals(full)) {
                status.setText("This number is already in the list.");
                return;
            }
        }

        String display = TextUtils.isEmpty(name) ? full : (name + " – " + full);

        numbers.add(display);
        adapter.notifyItemInserted(numbers.size() - 1);

        // clear inputs
        nameInput.setText("");
        phoneInput.setText("");

        status.setText("Added " + display + ". Drag to set priority (top = first).");
    }

    private void removeAt(int pos) {
        if (pos >= 0 && pos < numbers.size()) {
            String removed = numbers.remove(pos);
            adapter.notifyItemRemoved(pos);
            status.setText("Removed " + removed);
        }
    }

    private String generateId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random r = new java.util.Random();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(r.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void saveAndContinue() {
        if (numbers.isEmpty()) {
            status.setText("Add at least one emergency number before continuing.");
            return;
        }

        // Extract only numbers for dialing; keep labels for UI if you want
        List<String> pureList = new ArrayList<>();
        for (String s : numbers) {
            String num = s.contains(" – ") ? s.substring(s.indexOf(" – ") + 3).trim() : s.trim();
            pureList.add(num);
        }

        LinkedHashSet<String> orderedNumbers = new LinkedHashSet<>(pureList);
        LinkedHashSet<String> orderedDisplay  = new LinkedHashSet<>(numbers);

        String existingId = prefs.getString(KEY_SICK_ID, null);
        String sickId = (existingId != null) ? existingId : generateId();
        String sickName = nameInput.getText() != null ? nameInput.getText().toString().trim() : "";

        prefs.edit()
                .putStringSet("priority_numbers", orderedNumbers)
                .putStringSet("priority_labels", orderedDisplay)
                .putString(KEY_SICK_ID, sickId)
                .putString(KEY_SICK_NAME, sickName)
                .apply();

        // Also push to Firestore: base profile
        com.google.firebase.firestore.FirebaseFirestore db =
                com.google.firebase.firestore.FirebaseFirestore.getInstance();

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("role", "sick");
        data.put("name", sickName);
        data.put("id", sickId);
        data.put("createdAt", com.google.firebase.Timestamp.now());

        db.collection("users").document(sickId).set(data);

        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

}
