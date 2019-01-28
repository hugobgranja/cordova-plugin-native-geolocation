package com.github.hugobgranja;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.Manifest;
import android.location.Location;
import android.support.annotation.NonNull;
import android.util.SparseArray;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Geolocation extends CordovaPlugin implements OnLocationResultEventListener {

    private SparseArray<LocationContext> locationContexts;
    private FusedLocationProviderClient fusedLocationClient;

    public static final String[] permissions = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        locationContexts = new SparseArray<LocationContext>();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(cordova.getActivity());
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if(!checkGooglePlayServicesAvailable(callbackContext)) {
            return false;
        }

        if ("getLocation".equals(action)) {
            int id = args.getString(3).hashCode();
            LocationContext lc = new LocationContext(id, LocationContext.Type.RETRIEVAL, args, callbackContext, this);
            locationContexts.put(id, lc);

            if (hasPermission()) {
                getLocation(lc);
            } else {
                PermissionHelper.requestPermissions(this, id, permissions);
            }

        } else if ("addWatch".equals(action)) {
            int id = args.getString(0).hashCode();
            LocationContext lc = new LocationContext(id, LocationContext.Type.UPDATE, args, callbackContext, this);
            locationContexts.put(id, lc);

            if (hasPermission()) {
                addWatch(lc);
            } else {
                PermissionHelper.requestPermissions(this, id, permissions);
            }

        } else if ("clearWatch".equals(action)) {
            clearWatch(args, callbackContext);

        } else {
            return false;
        }

        return true;
    }

    private boolean hasPermission() {
        for (String permission : permissions) {
            if (!PermissionHelper.hasPermission(this, permission)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        LocationContext lc = locationContexts.get(requestCode);

        for (int grantResult : grantResults) {
            if (grantResult == PackageManager.PERMISSION_DENIED) {

                PluginResult result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION, LocationError.LOCATION_PERMISSION_DENIED.toJSON());
                lc.getCallbackContext().sendPluginResult(result);
                locationContexts.delete(lc.getId());
                return;
            }
        }

        switch(lc.getType()) {
            case RETRIEVAL:
                getLocation(lc);
                break;

            case UPDATE:
                addWatch(lc);
                break;
        }
    }

    private boolean checkGooglePlayServicesAvailable(CallbackContext callbackContext) {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int status = googleApiAvailability.isGooglePlayServicesAvailable(cordova.getActivity());

        if(status != ConnectionResult.SUCCESS) {
            PluginResult result;

            if(googleApiAvailability.isUserResolvableError(status)) {
                result = new PluginResult(PluginResult.Status.ERROR, LocationError.GOOGLE_SERVICES_ERROR_RESOLVABLE.toJSON());
            }
            else {
                result = new PluginResult(PluginResult.Status.ERROR, LocationError.GOOGLE_SERVICES_ERROR.toJSON());
            }

            callbackContext.sendPluginResult(result);
            return false;
        }

        return true;
    }

    private void getLocation(LocationContext locationContext) {
        JSONArray args = locationContext.getExecuteArgs();
        long timeout = args.optLong(2);
        boolean enableHighAccuracy = args.optBoolean(0, false);
        LocationRequest request = LocationRequest.create();

        request.setNumUpdates(1);

        // This is necessary to be able to get a response when location services are initially off and then turned on before this request.
        request.setInterval(4000);

        if(enableHighAccuracy) {
            request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        }

        if(timeout != 0) {
            request.setExpirationDuration(timeout);
        }

        requestLocationUpdatesIfSettingsSatisfied(locationContext, request);
    }

    private void addWatch(LocationContext locationContext) {
        JSONArray args = locationContext.getExecuteArgs();
        boolean enableHighAccuracy = args.optBoolean(1, false);
        LocationRequest request = LocationRequest.create();

        // TODO This should be an option?
        // Note: Seems like this is not necessary here in the case of a location services reset unlike getLocation
        request.setInterval(5000);

        if(enableHighAccuracy) {
            request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        }

        requestLocationUpdatesIfSettingsSatisfied(locationContext, request);
    }

    @SuppressLint("MissingPermission")
    private void requestLocationUpdates(LocationContext locationContext, LocationRequest request) {
        fusedLocationClient.requestLocationUpdates(request, locationContext.getLocationCallback(), null);
    }

    private void clearWatch(JSONArray args, CallbackContext callbackContext) {
        String id = args.optString(0);

        if(id != null) {
            int requestId = id.hashCode();
            LocationContext lc = locationContexts.get(requestId);

            if(lc == null) {
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, LocationError.WATCH_ID_NOT_FOUND.toJSON());
                callbackContext.sendPluginResult(result);
            }
            else {
                this.locationContexts.delete(requestId);
                fusedLocationClient.removeLocationUpdates(lc.getLocationCallback());

                PluginResult result = new PluginResult(PluginResult.Status.OK);
                callbackContext.sendPluginResult(result);
            }
        }
    }

    @Override
    public void onLocationResultSuccess(LocationContext locationContext, LocationResult locationResult) {
        for (Location location : locationResult.getLocations()) {
            try {
                JSONObject locationObject = LocationUtils.locationToJSON(location);
                PluginResult result = new PluginResult(PluginResult.Status.OK, locationObject);

                if (locationContext.getType() == LocationContext.Type.UPDATE) {
                    result.setKeepCallback(true);
                }
                else {
                    locationContexts.delete(locationContext.getId());
                }

                locationContext.getCallbackContext().sendPluginResult(result);

            } catch (JSONException e) {
                PluginResult result = new PluginResult(PluginResult.Status.JSON_EXCEPTION, LocationError.SERIALIZATION_ERROR.toJSON());

                if (locationContext.getType() == LocationContext.Type.UPDATE) {
                    result.setKeepCallback(true);
                }
                else {
                    locationContexts.delete(locationContext.getId());
                }

                locationContext.getCallbackContext().sendPluginResult(result);
            }
        }
    }

    @Override
    public void onLocationResultError(LocationContext locationContext, LocationError error) {
        PluginResult result = new PluginResult(PluginResult.Status.ERROR, error.toJSON());

        if (locationContext.getType() == LocationContext.Type.UPDATE) {
            result.setKeepCallback(true);
        }
        else {
            locationContexts.delete(locationContext.getId());
        }

        locationContext.getCallbackContext().sendPluginResult(result);
    }

    private void requestLocationUpdatesIfSettingsSatisfied(final LocationContext locationContext, final LocationRequest request) {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(request);
        SettingsClient client = LocationServices.getSettingsClient(cordova.getActivity());
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        OnSuccessListener<LocationSettingsResponse> checkLocationSettingsOnSuccess = new OnSuccessListener<LocationSettingsResponse>() {
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                // All location settings are satisfied. The client can initialize location requests here.
                requestLocationUpdates(locationContext, request);
            }
        };

        OnFailureListener checkLocationSettingsOnFailure = new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                PluginResult result;
                if (e instanceof ResolvableApiException) {
                    // Location settings are not satisfied, but this can be fixed.
                    result = new PluginResult(PluginResult.Status.ERROR, LocationError.LOCATION_SETTINGS_ERROR_RESOLVABLE.toJSON());
                }
                else {
                    result = new PluginResult(PluginResult.Status.ERROR, LocationError.LOCATION_SETTINGS_ERROR.toJSON());
                }

                locationContext.getCallbackContext().sendPluginResult(result);
                locationContexts.remove(locationContext.getId());
            }
        };

        task.addOnSuccessListener(checkLocationSettingsOnSuccess);
        task.addOnFailureListener(checkLocationSettingsOnFailure);
    }
}
