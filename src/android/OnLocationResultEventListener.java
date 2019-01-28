package com.github.hugobgranja;

import com.google.android.gms.location.LocationResult;

interface OnLocationResultEventListener {

    void onLocationResultSuccess(LocationContext locationContext, LocationResult result);
    void onLocationResultError(LocationContext locationContext, LocationError error);

}