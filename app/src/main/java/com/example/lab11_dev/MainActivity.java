package com.example.lab11_dev;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private MapView map;
    private Marker currentMarker;
    private static final int PERMISSION_REQUEST_CODE = 200;
    private LocationManager locationManager;
    private Location currentBestLocation = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Configuration pour osmdroid
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);

        // Position par défaut temporaire (Paris) pour le démarrage visuel
        GeoPoint startPoint = new GeoPoint(48.8583, 2.2944);
        map.getController().setZoom(18.0);
        map.getController().setCenter(startPoint);

        currentMarker = new Marker(map);
        currentMarker.setTitle("Recherche de votre position...");
        currentMarker.setPosition(startPoint);
        currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        map.getOverlays().add(currentMarker);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        startLocationUpdates();
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_CODE);
            return;
        }

        // 1. Essayer d'obtenir la dernière position connue immédiatement
        Location lastKnown = null;
        List<String> providers = locationManager.getProviders(true);
        for (String p : providers) {
            Location l = locationManager.getLastKnownLocation(p);
            if (l != null) {
                if (l.getLatitude() != 0.0 && l.getLongitude() != 0.0) {
                    if (lastKnown == null || l.getAccuracy() < lastKnown.getAccuracy()) {
                        lastKnown = l;
                    }
                }
            }
        }

        if (lastKnown != null) {
            currentBestLocation = lastKnown;
            updateUIWithLocation(lastKnown);
        }

        // 2. Écouter les mises à jour sur TOUS les services disponibles pour maximiser la précision
        boolean isAnyProviderEnabled = false;

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListener);
            isAnyProviderEnabled = true;
        }
        
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, locationListener);
            isAnyProviderEnabled = true;
        }

        if (!isAnyProviderEnabled) {
            buildAlertMessageNoGps();
        }
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            // Ignorer les positions (0,0) - Null Island
            if (location.getLatitude() == 0.0 && location.getLongitude() == 0.0) {
                return;
            }

            // Stratégie : On ne garde que la position si elle est plus précise que l'actuelle
            // ou si on n'en a pas encore.
            if (currentBestLocation == null || location.getAccuracy() < currentBestLocation.getAccuracy() + 10) {
                currentBestLocation = location;
                updateUIWithLocation(location);
            }
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
            // Optionnel : ne pas harceler l'utilisateur si un seul provider est coupé
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && 
                !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                buildAlertMessageNoGps();
            }
        }

        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
        @Override public void onProviderEnabled(@NonNull String provider) {}
    };

    private void updateUIWithLocation(Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        GeoPoint position = new GeoPoint(latitude, longitude);
        
        if (currentMarker == null) {
            currentMarker = new Marker(map);
            map.getOverlays().add(currentMarker);
        }
        
        currentMarker.setPosition(position);
        currentMarker.setTitle("Ma position exacte");
        currentMarker.setSnippet("Précision: " + (int)location.getAccuracy() + "m (" + location.getProvider() + ")");
        
        map.getController().animateTo(position);
        map.invalidate();
    }

    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Votre GPS est désactivé. Pour une position précise, merci de l'activer.")
                .setCancelable(false)
                .setPositiveButton("Paramètres", (dialog, id) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                .setNegativeButton("Annuler", (dialog, id) -> dialog.cancel());
        final AlertDialog alert = builder.create();
        alert.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                Toast.makeText(this, "Permission de localisation refusée", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
        locationManager.removeUpdates(locationListener);
    }
}
