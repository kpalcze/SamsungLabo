package com.example.k.airportfinder;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;

import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private LocationManager locationManager;
    private LocationListener listener;
    private Location userLocation;
    private LatLng mLatLng;
    private GoogleApiClient mGoogleApiClient;
    private SupportMapFragment mapFragment;
    private Boolean mapReady = false, isConnectedToInternet = false, hasWaited = true;
    private GoogleMap mGoogleMap;
    private Marker currentPosition, airportPosition;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(LocationServices.API)
                    .build();
        }

        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location loc) {
                userLocation = loc;
                mLatLng = new LatLng(userLocation.getLatitude(), userLocation.getLongitude());
                Handler mHandler = new Handler();
                Log.d("before handler", "" + hasWaited);
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        hasWaited = true;
                        Log.d("handler", "waiting" + hasWaited);
                    }
                }, 10000);
                if (hasWaited) {
                    Log.d("hasWaited", "after" + hasWaited);
                    new DownloadNearestAirportLocation().execute(createUrl());
                    hasWaited = false;
                }

                if (mapReady) {
                    UpdateMap(mLatLng);
                }
            }

            @Override
            public void onStatusChanged(String s, int i, Bundle bundle) {

            }

            @Override
            public void onProviderDisabled(String provider) {
                Toast.makeText(getBaseContext(), "Gps turned off ", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onProviderEnabled(String provider) {
                Toast.makeText(getBaseContext(), "Gps turned on ", Toast.LENGTH_LONG).show();
            }
        };
        configure();
        checkInternetConnection();
        if (isConnectedToInternet) {
            new DownloadNearestAirportLocation().execute(createUrl());
        }
        mapFragment.getMapAsync(this);
    }

    protected void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
    }

    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case 10:
                configure();
                break;
            default:
                break;
        }
    }

    void configure(){
        // first check for permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.INTERNET}
                        ,10);
            }
            return;
        } else {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 10, listener);
            userLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (userLocation != null){
                Log.d("userLocation", "" + userLocation.getLongitude() + " " + userLocation.getLatitude());
            } else {
            }
        }
    }

    public void onMapReady(GoogleMap googleMap) {
        mapReady = true;
        mGoogleMap = googleMap;
        LatLng mapLocation = new LatLng(userLocation.getLatitude(), userLocation.getLongitude());
        currentPosition = mGoogleMap.addMarker(new MarkerOptions().position(mapLocation)
                .title("You are here"));
        mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mapLocation, 8));
    }

    private void UpdateMap(LatLng newLoc) {
        currentPosition.remove();
        currentPosition = mGoogleMap.addMarker(new MarkerOptions().position(newLoc)
                .title("You are here"));
        mGoogleMap.moveCamera(CameraUpdateFactory.newLatLng(newLoc));
    }

    private void AddAirportMarkerToMap(LatLng airportLoc, String name, String distance) {
        if (airportPosition != null) {
            airportPosition.remove();
        }
        airportPosition = mGoogleMap.addMarker(new MarkerOptions().position(airportLoc)
                .title(name + " - " + distance + "km away"));
    }

    private String createUrl() {
        String url = null;
        String base = "https://maps.googleapis.com/maps/api/place";
        String searchtype = "/nearbysearch/json";
        String loc = "?location=" + Double.toString(userLocation.getLatitude()) + "," + Double.toString(userLocation.getLongitude());
        String radius = "&radius=50000";
        String types = "&types=airport";
        String apiKey = "&key=" + getResources().getString(R.string.web_service_key);
        StringBuilder sb = new StringBuilder(base);
        sb.append(searchtype).append(loc).append(radius).append(types).append(apiKey);
        url = sb.toString();
        Log.d("url", url);
        return url;
    }

    private void checkInternetConnection(){
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInf = connMgr.getActiveNetworkInfo();
        if (netInf != null && netInf.isConnected()){
            isConnectedToInternet= true;
        } else{
            isConnectedToInternet = false;
            Context context = getApplicationContext();
            CharSequence text = "Turn Wi-Fi or Mobile Data on";
            int duration = Toast.LENGTH_SHORT;
            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
            Log.e("error", "Connection error");
        }
    }

    private class DownloadNearestAirportLocation extends AsyncTask<String, Void, Result> {
        protected void onPreExecute() {
            super.onPreExecute();
        }
        protected Result doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);

                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

                InputStream stream = new BufferedInputStream(urlConnection.getInputStream());
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream));
                StringBuilder builder = new StringBuilder();

                String inputString;
                while ((inputString = bufferedReader.readLine()) != null) {
                    builder.append(inputString);
                }

                JSONObject json = new JSONObject(builder.toString());
                urlConnection.disconnect();

                Gson gson = new Gson();

                Place place = gson.fromJson(json.toString(), Place.class);
                Result nearestAirport;
                if (place != null) {
                    List<Result> results = place.getResults();

                    if (results.size() > 0) {
                        nearestAirport = results.get(0);
                    } else {
                        nearestAirport = null;
                        Log.e("jsonerror", "Error obtaining userLocation from downlaoded data");
                    }
                } else {
                    nearestAirport = null;
                    Log.e("jsonerror", "Error obtaining userLocation from downlaoded data");
                }
                return nearestAirport;
            } catch (IOException e) {
                Log.e("jsonerror", "JSON file could not be read");
            } catch (JSONException e) {
                Log.e("jsonerror", "String could not be converted to JSONObject");
            }
            return null;
        }

        protected void onPostExecute(Result nearestAirport) {
                if (nearestAirport != null) {
                    Geometry geometry1 = nearestAirport.getGeometry();
                    com.example.k.airportfinder.Location location1 = geometry1.getLocation();
                    LatLng airportLocation = new LatLng(location1.getLat(), location1.getLng());
                    Location nearestAirportLoc = new Location("");
                    nearestAirportLoc.setLatitude(location1.getLat());
                    nearestAirportLoc.setLongitude(location1.getLng());
                    String name = nearestAirport.getName();
                    String distance = String.format("%.2f", userLocation.distanceTo(nearestAirportLoc) / 1000);
                    String.format("%.2f", 1.2975118);
                    AddAirportMarkerToMap(airportLocation, name, distance);
                } 
            }
        }
    }