package com.example.socket;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

public class SocketPackage implements Serializable {

    private final String TAG = "SocketPackage";
    private JSONObject mObj = null;


    public SocketPackage(JSONObject obj) {
        mObj = obj;
    }

    public SocketPackage() {
        this(new JSONObject());
    }

    public JSONObject getObj() {
        return mObj;
    }

    public static byte[] convertToByteArray(SocketPackage p) {
        JSONObject obj = p.getObj();
        if(obj != null) {
            return obj.toString().getBytes();
        }
        return null;
    }

    public static SocketPackage convertToSocketPackage(byte[] b) {
        SocketPackage p = null;
        try {
            JSONObject obj = new JSONObject(new String(b).trim());
            p = new SocketPackage(obj);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return p;
    }

    public String getSourceIp() {
        if(checkObject()) {
            JSONObject j = mObj.optJSONObject("src");
            if(j == null) {
                return null;
            }
            return j.optString("ip");
        }
        return null;
    }

    public int getSourcePort() {
        if(checkObject()) {
            JSONObject j = mObj.optJSONObject("src");
            if(j == null) {
                return 0;
            }
            return j.optInt("port");
        }
        return 0;
    }

    public boolean putSourceAddr(String ip, int port) {
        if(checkObject()) {
            JSONObject src = new JSONObject();
            try {
                src.put("ip", ip);
                src.put("port", port);
                mObj.put("src", src);
            } catch (JSONException e) {
                e.printStackTrace();
                return false;
            }
            return true;
        }
        return false;
    }

    public boolean putRequest(JSONObject r) {
        if(r == null) {
            Log.e(TAG, "string is null");
            return false;
        }
        if(checkObject()) {
            JSONArray array = tryGetArray("request");
            if(array == null) {
                array = new JSONArray();
            }
            array.put(r);
            return tryPutArray("request", array);
        }
        return false;
    }

    public JSONObject[] getRequest() {
        if(checkObject()) {
            JSONArray array = tryGetArray("request");
            return toJSONObjectArray(array);
        }
        return null;
    }

    public boolean putResponse(JSONObject obj) {
        if(obj == null) {
            Log.e(TAG, "object is null");
            return false;
        }
        if(checkObject()) {
            JSONArray array = tryGetArray("response");
            if(array == null) {
                array = new JSONArray();
            }
            array.put(obj);
            tryPutArray("response", array);
        }
        return false;
    }

    public JSONObject[] getResponse() {
        if(checkObject()) {
            JSONArray array = tryGetArray("response");
            return toJSONObjectArray(array);
        }
        return null;
    }

    private JSONObject[] toJSONObjectArray(JSONArray array) {
        if(array == null)
            return null;
        int size = array.length();
        if(size != 0) {
            JSONObject[] r = new JSONObject[size];
            for(int i=0; i<size; i++) {
                r[i] = array.optJSONObject(i);
            }
            return r;
        }
        return null;
    }

    private JSONArray tryGetArray(String item) {
        JSONObject obj = mObj.optJSONObject(item);
        if(obj == null) {
            return null;
        }
        return obj.optJSONArray("item");
    }

    private boolean tryPutArray(String item, JSONArray array) {
        JSONObject obj = mObj.optJSONObject(item);
        if(obj == null ) {
            obj = new JSONObject();
        }
        try {
            obj.put("item", array);
            mObj.put(item, obj);
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private boolean checkObject() {
        if(mObj == null) {
            Log.e(TAG, "JSONObject is null");
            return false;
        }
        return true;
    }
}
