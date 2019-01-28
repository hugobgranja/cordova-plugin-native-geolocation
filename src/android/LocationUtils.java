package com.github.hugobgranja;

import android.location.Location;

import org.json.JSONException;
import org.json.JSONObject;

public class LocationUtils {

    public static JSONObject locationToJSON(Location location) throws JSONException {
        JSONObject obj = new JSONObject();

        obj.put("longitude", location.getLongitude());
        obj.put("latitude", location.getLatitude());
        obj.put("altitude", location.getAltitude());
        obj.put("accuracy", location.getAccuracy());
        obj.put("heading", location.getBearing());
        obj.put("velocity", location.getSpeed());
        obj.put("timestamp", location.getTime());
        obj.put("speed", location.getSpeed());

        return obj;
    }

}