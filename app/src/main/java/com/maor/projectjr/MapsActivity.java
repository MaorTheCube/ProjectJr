package com.maor.projectjr;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.appbar.MaterialToolbar;
import com.maor.projectjr.databinding.ActivityMapsBinding;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LNG = "lng";
    public static final String EXTRA_PATIENT_NAME = "patient_name";

    private ActivityMapsBinding binding;
    private double lat = Double.NaN;
    private double lng = Double.NaN;
    private String patientName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        MaterialToolbar toolbar = findViewById(R.id.maps_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        lat = getIntent().getDoubleExtra(EXTRA_LAT, Double.NaN);
        lng = getIntent().getDoubleExtra(EXTRA_LNG, Double.NaN);
        patientName = getIntent().getStringExtra(EXTRA_PATIENT_NAME);
        if (patientName != null && !patientName.isEmpty()) {
            toolbar.setTitle(patientName + "'s location");
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        if (Double.isNaN(lat) || Double.isNaN(lng)) return;

        LatLng position = new LatLng(lat, lng);
        String title = (patientName != null && !patientName.isEmpty()) ? patientName : "Patient";
        googleMap.addMarker(new MarkerOptions().position(position).title(title));
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 15f));
    }
}
