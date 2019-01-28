package com.github.hugobgranja;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;

import org.apache.cordova.CallbackContext;
import org.json.JSONArray;

public class LocationContext {

    public enum Type {
        RETRIEVAL,
        UPDATE
    }

    private int id;
    private LocationContext.Type type;
    private JSONArray executeArgs;
    private CallbackContext callbackContext;
    private LocationCallback locationCallback;
    private final OnLocationResultEventListener listener;

    public LocationContext(int id, LocationContext.Type type, JSONArray executeArgs, CallbackContext callbackContext, OnLocationResultEventListener listener) {
        this.id = id;
        this.type = type;
        this.executeArgs = executeArgs;
        this.callbackContext = callbackContext;
        this.listener = listener;

        this.locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if(LocationContext.this.listener != null) {
                    if(locationResult == null) {
                        LocationContext.this.listener.onLocationResultError(LocationContext.this, LocationError.LOCATION_NULL);
                    }
                    else {
                        LocationContext.this.listener.onLocationResultSuccess(LocationContext.this, locationResult);
                    }
                }
            }
        };
    }

    public int getId() {
        return id;
    }

    public Type getType() {
        return type;
    }

    public JSONArray getExecuteArgs() {
        return executeArgs;
    }

    public CallbackContext getCallbackContext() {
        return callbackContext;
    }

    public LocationCallback getLocationCallback() {
        return locationCallback;
    }

}
