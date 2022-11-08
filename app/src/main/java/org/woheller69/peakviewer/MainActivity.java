package org.woheller69.peakviewer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentManager;
import java.util.ArrayList;
import org.woheller69.photondialog.City;
import org.woheller69.photondialog.PhotonDialog;

@SuppressLint("SetJavaScriptEnabled")
    public class MainActivity extends AppCompatActivity implements PhotonDialog.PhotonDialogResult {
        private static LocationListener locationListenerGPS;
        private LocationManager locationManager;
        private static MenuItem updateLocationButton;
        private WebView peakWebView = null;
        private WebSettings mapsWebSettings = null;
        private CookieManager mapsCookieManager = null;
        private SensorManager sensorManager;
        private SensorEventListener sensorListener;
        private double azimut = 0;

        private static final ArrayList<String> allowedDomains = new ArrayList<>();

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            setContentView(R.layout.activity_main);
            if (getSupportActionBar()!=null) getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getColor(R.color.grey)));
            peakWebView = findViewById(R.id.peakViewer);

            //Set cookie options
            mapsCookieManager = CookieManager.getInstance();
            mapsCookieManager.setAcceptCookie(true);
            mapsCookieManager.setAcceptThirdPartyCookies(peakWebView, false);

            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            //Delete anything from previous sessions
            resetWebView(false);

            //Restrict what gets loaded
            initURLs();

            peakWebView.setWebViewClient(new WebViewClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    boolean allowed = false;
                    for (String url : allowedDomains) {
                        if (request.getUrl().getHost().endsWith(url)) {
                            //Log.d(getString(R.string.app_name), "Allowed access to " + request.getUrl().getHost());
                            allowed = true;
                        }
                    }
                    if (allowed) return null;  //continue loading
                    else {
                        //Log.d(getString(R.string.app_name), "Blocked access to " + request.getUrl().getHost());
                        return new WebResourceResponse("text/javascript","UTF-8",null);  //replace with null content
                    }
                }

            });

            mapsWebSettings = peakWebView.getSettings();
            mapsWebSettings.setDisplayZoomControls(true);
            mapsWebSettings.setDomStorageEnabled(true);
            mapsWebSettings.setJavaScriptEnabled(true);
            mapsWebSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
            mapsWebSettings.setAllowContentAccess(false);
            mapsWebSettings.setAllowFileAccess(false);
            mapsWebSettings.setDatabaseEnabled(false);

        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            resetWebView(true);
            if (sensorManager != null) {
                sensorManager.unregisterListener(sensorListener);
            }
        }


        private void resetWebView(boolean exit) {
            if (exit) {
                peakWebView.loadUrl("about:blank");
                peakWebView.removeAllViews();
                mapsWebSettings.setJavaScriptEnabled(false);
            }

            peakWebView.clearFormData();
            peakWebView.clearHistory();
            peakWebView.clearMatches();
            peakWebView.clearSslPreferences();
            mapsCookieManager.removeSessionCookies(null);
            mapsCookieManager.removeAllCookies(null);
            mapsCookieManager.flush();
            //WebStorage.getInstance().deleteAllData();
            if (exit) {
                peakWebView.destroy();
                peakWebView = null;
            }
        }

        private static void initURLs() {
            //Allowed Domains
            allowedDomains.add("www.peakfinder.org");
            allowedDomains.add("service.peakfinder.org");
            //allowedDomains.add("kxcdn.com");  //without some info is missing in the info window at the bottom. But not sure if kxcdn.com should be trusted
        }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        final Menu m = menu;
        updateLocationButton = menu.findItem(R.id.menu_update_location);
        updateLocationButton.setActionView(R.layout.menu_update_location_view);
        updateLocationButton.getActionView().setOnClickListener(v -> m.performIdentifierAction(updateLocationButton.getItemId(), 0));
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ) {

            updateLocationButton.getActionView().clearAnimation();
            if (locationListenerGPS!=null) {  //GPS still trying to get new location -> stop and restart to get around problem with tablayout not updating
                removeLocationListener();
                    locationListenerGPS=getNewLocationListener();
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListenerGPS);
                    if (updateLocationButton != null && updateLocationButton.getActionView() != null) {
                        startUpdateLocatationAnimation();
                }
            }
        }else{
            removeLocationListener();
            if (updateLocationButton != null && updateLocationButton.getActionView() != null) {
                updateLocationButton.getActionView().clearAnimation();
            }
        }

        return true;
    }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            if (item.getItemId()==R.id.menu_update_location){

                locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
                if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
                    Toast.makeText(this,R.string.error_no_gps,Toast.LENGTH_LONG).show();
                } else {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        if (locationListenerGPS == null) {
                            Log.d("GPS", "Listener null");
                            locationListenerGPS = getNewLocationListener();
                            startUpdateLocatationAnimation();
                            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListenerGPS);
                        }
                    } else {
                        ActivityCompat.requestPermissions(this,
                                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                    }
                }

            } else if (item.getItemId()==R.id.menu_telescope){
                peakWebView.loadUrl("javascript:showTelescope();");
            }  else if (item.getItemId()==R.id.menu_search){

        FragmentManager fragmentManager = getSupportFragmentManager();
        PhotonDialog photonDialog = new PhotonDialog();
        photonDialog.setTitle("Search");
        photonDialog.setNegativeButtonText("Cancel");
        photonDialog.setPositiveButtonText("Select");
        photonDialog.setUserAgentString(BuildConfig.APPLICATION_ID+"/"+BuildConfig.VERSION_NAME);
        photonDialog.show(fragmentManager, "");
        getSupportFragmentManager().executePendingTransactions();
        photonDialog.getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

    } else if (item.getItemId()==R.id.menu_compass){
                if (sensorListener!=null){
                    sensorManager.unregisterListener(sensorListener);
                    sensorListener=null;
                    item.setIcon(R.drawable.ic_compass_off_24dp);
                } else {
                    if (sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null && sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
                        startCompass();
                        item.setIcon(R.drawable.ic_compass_24dp);
                    } else
                        Toast.makeText(this,"No Compass",Toast.LENGTH_LONG).show();
                }
            }
          return true;
        }

    private void startCompass() {
            Sensor accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            Sensor magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            sensorListener = new SensorEventListener() {
                float [] accValues = new float[3];
                float [] magValues = new float[3];
                public void onAccuracyChanged(Sensor sensor, int accuracy) {
                }

                public void onSensorChanged(SensorEvent event) {
                    if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                        accValues = event.values.clone();
                    } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD){
                        magValues = event.values.clone();
                    }
                    float[] R = new float[9];
                    float[] Rot = new float[9];
                    float[] values = new float[3];

                    //Fix orientation
                    //https://stackoverflow.com/questions/18782829/android-sensormanager-strange-how-to-remapcoordinatesystem
                    boolean success = SensorManager.getRotationMatrix(R,null,accValues,magValues);
                    if (success) {
                        int screenRotation = getWindowManager().getDefaultDisplay().getRotation();
                        int axisX, axisY;
                        boolean isUpSideDown = accValues[2] < 0;

                        switch (screenRotation) {
                            case Surface.ROTATION_0:
                                axisX = (isUpSideDown ? SensorManager.AXIS_MINUS_X : SensorManager.AXIS_X);
                                axisY = (Math.abs(accValues[1]) > 0.0f ?
                                        (isUpSideDown ? SensorManager.AXIS_MINUS_Z : SensorManager.AXIS_Z) :
                                        (isUpSideDown ? SensorManager.AXIS_MINUS_Y : SensorManager.AXIS_Y));
                                break;
                            case Surface.ROTATION_90:
                                axisX = (isUpSideDown ? SensorManager.AXIS_MINUS_Y : SensorManager.AXIS_Y);
                                axisY = (Math.abs(accValues[0]) > 0.0f ?
                                        (isUpSideDown ? SensorManager.AXIS_Z : SensorManager.AXIS_MINUS_Z) :
                                        (isUpSideDown ? SensorManager.AXIS_X : SensorManager.AXIS_MINUS_X));
                                break;
                            case  Surface.ROTATION_180:
                                axisX = (isUpSideDown ? SensorManager.AXIS_X : SensorManager.AXIS_MINUS_X);
                                axisY = (Math.abs(accValues[1]) > 0.0f ?
                                        (isUpSideDown ? SensorManager.AXIS_Z : SensorManager.AXIS_MINUS_Z) :
                                        (isUpSideDown ? SensorManager.AXIS_Y : SensorManager.AXIS_MINUS_Y));
                                break;
                            case Surface.ROTATION_270:
                                axisX = (isUpSideDown ? SensorManager.AXIS_Y : SensorManager.AXIS_MINUS_Y);
                                axisY = (Math.abs(accValues[0]) > 0.0f ?
                                        (isUpSideDown ? SensorManager.AXIS_MINUS_Z : SensorManager.AXIS_Z) :
                                        (isUpSideDown ? SensorManager.AXIS_MINUS_X : SensorManager.AXIS_X));
                                break;
                            default:
                                axisX = (isUpSideDown ? SensorManager.AXIS_MINUS_X : SensorManager.AXIS_X);
                                axisY = (isUpSideDown ? SensorManager.AXIS_MINUS_Y : SensorManager.AXIS_Y);
                        }

                        SensorManager.remapCoordinateSystem(R, axisX, axisY, Rot);
                        SensorManager.getOrientation(Rot,values);

                        double newAzimut = values[0];
                        azimut=Math.atan2(15*Math.sin(azimut)+Math.sin(newAzimut),15*Math.cos(azimut)+Math.cos(newAzimut));
                        peakWebView.loadUrl("javascript:setAzimut("+(Math.toDegrees(azimut)+360)%360+");");
                    }
                }
            };

            sensorManager.registerListener(sensorListener, accelSensor, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(sensorListener, magSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void removeLocationListener() {
        if (locationListenerGPS!=null) {
            locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
            if (locationListenerGPS!=null) locationManager.removeUpdates(locationListenerGPS);
        }
        locationListenerGPS=null;
    }

    private LocationListener getNewLocationListener() {
        return new LocationListener() {
            @Override
            public void onLocationChanged(android.location.Location location) {

                String urlToLoad = String.format(
                        "file:///android_asset/canvas.html?lat=%s&lon=%s&units=0&night=%s",
                        location.getLatitude(),
                        location.getLongitude(),
                        getNightMode()
                );
                findViewById(R.id.main_background).setVisibility(View.GONE);  //Remove background image
                peakWebView.setVisibility(View.VISIBLE);
                peakWebView.loadUrl(urlToLoad);

                removeLocationListener();
                if (updateLocationButton != null && updateLocationButton.getActionView() != null) {
                    updateLocationButton.getActionView().clearAnimation();
                }
            }

            @Deprecated
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
    }

    public static void startUpdateLocatationAnimation(){
        {
            if(updateLocationButton !=null && updateLocationButton.getActionView() != null) {
                Animation blink = new AlphaAnimation(1, 0); // Change alpha from fully visible to invisible
                blink.setDuration(1000);
                blink.setRepeatCount(Animation.INFINITE);
                blink.setInterpolator(new LinearInterpolator());
                blink.setRepeatMode(Animation.REVERSE);
                blink.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                        updateLocationButton.getActionView().setActivated(false);
                        updateLocationButton.getActionView().setEnabled(false);
                        updateLocationButton.getActionView().setClickable(false);
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        updateLocationButton.getActionView().setActivated(true);
                        updateLocationButton.getActionView().setEnabled(true);
                        updateLocationButton.getActionView().setClickable(true);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }
                });
                updateLocationButton.getActionView().startAnimation(blink);
            }
        }
    }
    private int getNightMode(){
        int nightModeFlags =getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES ? 1 : 0 ;
    }

    @Override
    public void onPhotonDialogResult(City city) {

        String urlToLoad = String.format(
                "file:///android_asset/canvas.html?lat=%s&lon=%s&units=0&night=%s",
                city.getLatitude(),
                city.getLongitude(),
                getNightMode()
        );
        findViewById(R.id.main_background).setVisibility(View.GONE);  //Remove background image
        peakWebView.setVisibility(View.VISIBLE);
        peakWebView.loadUrl(urlToLoad);

    }
}
