/*
 * Copyright (c) 2017 Angelina Scheler und Daniel Glaser
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


package de.hof.university.app.fragment;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.util.CloudmadeUtil;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.TilesOverlay;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

import de.hof.university.app.MainActivity;
import de.hof.university.app.R;

/**
 * Created by angelina on 18.01.17.
 */

public class MapFragment extends android.support.v4.app.Fragment {

    final private int REQUEST_CODE_ASK_PERMISSIONS = 1;
    private final static String TAG = "MapFragment";

    private MapView myOpenMapView;
    private IMapController myMapController;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Marker marker;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Speicherort für OSMDroid auf persönlichen Ordner der App setzen
        Configuration.getInstance().setOsmdroidBasePath(new File(getActivity().getFilesDir(), "OSMDroidBase"));
        Configuration.getInstance().setOsmdroidTileCache(new File(getActivity().getFilesDir(), "OSMDroidTileCache"));

        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        myOpenMapView = (MapView) view.findViewById(R.id.mapView);
        myOpenMapView.setBuiltInZoomControls(true);
        myOpenMapView.setMultiTouchControls(true);
        // Falls man möchte das die Karte immmer zur aktuellen Position springt
        //myOpenMapView.setFlingEnabled(true);
        // Falls man möchte das man näher ran gezoomt ist
        myOpenMapView.setTilesScaledToDpi(true);
        myOpenMapView.getOverlays().add(new CopyrightOverlay(getActivity().getApplicationContext()));

        myMapController = myOpenMapView.getController();
        myMapController.setZoom(40);
        marker = new Marker(myOpenMapView);

        marker.setIcon(getResources().getDrawable(R.drawable.pin));

        myOpenMapView.getOverlays().add(marker);


        CloudmadeUtil.DEBUGMODE = true;

        TilesOverlay x = this.myOpenMapView.getOverlayManager().getTilesOverlay();
        x.setOvershootTileCache(x.getOvershootTileCache() * 2);

        locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                updateLoc(location);
                updateLocationInfo(location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };


        // Witch Android Version?
        // before Android 6.0 Marshmallow
        if (Build.VERSION.SDK_INT < 23) {
            // überprüfen ob NETWORK_PROVIDER vorhanden ist sonst stützt die App ab
            if (locationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER) && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
            @SuppressLint("MissingPermission") Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastLocation != null) {
                updateLoc(lastLocation);
            }
        } else {
            // Android Marshmallow or higher
            if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // Einen Dialog bringen wenn der Nutzer den Haken bei "Nicht noch einmal anzeigen" setzt oder beim ersten Mal
                if (!ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION)) {
                    new AlertDialog.Builder(getView().getContext())
                            .setTitle(getString(R.string.needPermissionTitle))
                            .setMessage(getString(R.string.needPermissionText))
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    //ask for permission
                                    requestPermission();
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    //nothing to do here. Just close the message
                                }
                            })
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();
                    return;
                }
                //ask for permission
                requestPermission();
            } else {
                // we have permission
                // überprüfen ob NETWORK_PROVIDER vorhanden ist sonst stützt die App ab
                if (locationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER) && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
                }
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
                final Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastLocation != null) {
                    updateLoc(lastLocation);
                }
            }
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT > 23) {
            this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_CODE_ASK_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE_ASK_PERMISSIONS) {
            if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);

                    // überprüfen ob NETWORK_PROVIDER vorhanden ist sonst stützt die App ab
                    if (locationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER) && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
                    }
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        MainActivity mainActivity = (MainActivity) getActivity();
        mainActivity.getSupportActionBar().setTitle(Html.fromHtml("<font color='"+ ContextCompat.getColor(MainActivity.getAppContext(), R.color.colorBlack)+"'>"+ getString(R.string.myposition)+"</font>"));
        mainActivity.getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_menu_accent_24dp);

        NavigationView navigationView = (NavigationView) mainActivity.findViewById(R.id.nav_view);
        MenuItem item = navigationView.getMenu().findItem(R.id.nav_experimental);
        //item.setChecked(true);
        item.getSubMenu().findItem(R.id.nav_map).setChecked(true);

        if ((ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
		        && (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        // TODO überprüfen ob NETWORK_PROVIDER vorhanden ist sonst stützt die App ab
        if (locationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER) && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
    }

    @Override
    public void onPause() {
        super.onPause();
        MainActivity mainActivity = (MainActivity) getActivity();
        NavigationView navigationView = (NavigationView) mainActivity.findViewById(R.id.nav_view);
        navigationView.getMenu().findItem(R.id.nav_experimental).getSubMenu().findItem(R.id.nav_map).setChecked(false);

        if ((ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
		        && (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        locationManager.removeUpdates(locationListener);
    }

    private void updateLoc(final Location loc) {
        GeoPoint locGeoPoint = new GeoPoint(loc.getLatitude(), loc.getLongitude());
        myMapController.setCenter(locGeoPoint);
        myOpenMapView.invalidate();
        updateLocationInfo(loc);
        marker.setPosition(locGeoPoint);
    }

    private void updateLocationInfo(final Location location) {
        Log.i("LocationInfo", location.toString());
        TextView addressTextView = (TextView) getView().findViewById(R.id.addressTextView);

        final Geocoder geocoder = new Geocoder(getActivity(), Locale.getDefault());

        try {
            String address = getString(R.string.no_address);

            final List<Address> listAddresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);

            if ((listAddresses != null) && !listAddresses.isEmpty()) {
                Log.i("PlaceInfo", listAddresses.get(0).toString());

                address = getString(R.string.address) + "\n";

                if (listAddresses.get(0).getThoroughfare() != null) {
                    address += listAddresses.get(0).getThoroughfare() + " ";
                }
                if (listAddresses.get(0).getSubThoroughfare() != null) {
                    address += listAddresses.get(0).getSubThoroughfare() + "\n";
                }
                if (listAddresses.get(0).getLocality() != null) {
                    address += listAddresses.get(0).getLocality() + "\n";
                }
                if (listAddresses.get(0).getPostalCode() != null) {
                    address += listAddresses.get(0).getPostalCode() + "\n";
                }
                if (listAddresses.get(0).getCountryName() != null) {
                    address += listAddresses.get(0).getCountryName() + "\n";
                }
            }
            addressTextView.setText(address);
        } catch (IOException e) {
            Log.e(TAG, "updateLocationInfo failed");
        }
    }
}
