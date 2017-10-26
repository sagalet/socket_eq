package com.jshipster.app.eq.eq;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import com.jshipster.eq.Equalizer;
import com.jshipster.eq.EqualizerImpl;
import com.jshipster.socket.NetworkChecker;
import com.jshipster.socket.SocketConnector;
import com.jshipster.socket.SocketPackage;
import com.jshipster.socket.UDPBroadcast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.UnknownHostException;

public class EqualizerService extends Service {

    private final String TAG = "EqualizerService";
    private EqualizerBinder mBinder = null;

    private final int GET_UDP_DATA = 0x1;
    private final int GET_TCP_DATA = 0x2;
    private final int GET_CLIENT_STATUS = 0x4;
    private final int GET_REMOTE_STATUS = 0x8;
    private final int STOP_UDP_RECEIVER = 0x10;
    private final int CONNECT_TO_REMOTE = 0x20;
    private final int SEND_UDP_BROADCAST =0x100;
    private final int TCP = 0x1000;
    private final int UDP = 0x2000;
    private int mUDPPort = 9010;
    private int mTCPPort = 9020;

    private UDPBroadcast mUDPBroadcast = null;
    private SocketConnector mSocketConnector = null;
    private Handler mHandler = null;
    private HandlerThread mThread = null;
    private NetworkChecker mNetwork = null;

    private String mSender = null;
    private String mReceiver = null;
    private BroadcastReceiver mBroadcastReceiver = null;
    private EqualizerImpl mEq = null;

    @Override
    public void onCreate() {
        // The service is being created
        Log.i(TAG, "onCreate");
        mEq = EqualizerImpl.getEqualizer();
        mBinder = new LocalBinder();

        mThread = new HandlerThread("EqualizerService Handler");
        mThread.start();
        mHandler = new Handler(mThread.getLooper()){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch(msg.what) {
                    case GET_UDP_DATA: {
                        SocketPackage p = (SocketPackage)msg.getData().getSerializable("data");
                        String ip = msg.getData().getString("ip");
                        Log.i(TAG, "get udp data from "+ip+" data="+p.getObj().toString());
                        parsePackage(p, UDP);
                        break;
                    }
                    case GET_TCP_DATA: {
                        SocketPackage p = (SocketPackage)msg.getData().getSerializable("data");
                        String ip = msg.getData().getString("ip");
                        Log.i(TAG, "get tcp data from "+ip+" data="+p.getObj().toString());
                        parsePackage(p, TCP);
                        break;
                    }
                    case GET_CLIENT_STATUS: {
                        String client = msg.getData().getString("client");
                        int status = msg.getData().getInt("status");
                        Log.i(TAG,"client="+client+" status="+status);
                        if(status == SocketConnector.STATUS_CONNECTED) {
                            mReceiver = client;
                            mUDPBroadcast.disableReceiver();
                            try {
                                mSocketConnector.stopListening();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else if(status == SocketConnector.STATUS_DISCONNECTED) {
                            mReceiver = null;
                            mUDPBroadcast.enableReceiver(1000);
                            mSocketConnector.startListening(mTCPPort, 1000);
                        }
                        break;
                    }
                    case GET_REMOTE_STATUS: {
                        String remote = msg.getData().getString("remote");
                        int status = msg.getData().getInt("status");
                        Log.i(TAG,"remote="+remote+" status="+status);
                        if(status == SocketConnector.STATUS_CONNECTED) {
                            mSender = remote;
                        } else if(status == SocketConnector.STATUS_DISCONNECTED){
                            mSender = null;
                        }
                    }
                    case STOP_UDP_RECEIVER: {
                        break;
                    }
                    case SEND_UDP_BROADCAST: {
                        break;
                    }
                    default:
                        Log.e(TAG,"get error msg("+msg.what+")");
                }
            }
        };

        mUDPBroadcast = new UDPBroadcast(mUDPPort);

        mUDPBroadcast.setCallback(new UDPBroadcast.EventCallback() {
            @Override
            public void onDataReceive(String ip, SocketPackage data) {
                Bundle b = new Bundle();
                b.putSerializable("data", data);
                b.putString("ip", ip);
                Log.e(TAG, "data="+data.getObj().toString());
                Message msg = mHandler.obtainMessage(GET_UDP_DATA);
                msg.setData(b);
                mHandler.sendMessage(msg);
            }
        });

        mSocketConnector = new SocketConnector();

        mSocketConnector.setCallback(new SocketConnector.EventCallback() {
            @Override
            public void onDataReceive(String from, SocketPackage p) {
                Log.i(TAG,"receive from "+from+" data="+p.getObj().toString());
                Message msg = mHandler.obtainMessage(GET_TCP_DATA);
                Bundle b = new Bundle();
                b.putSerializable("data", p);
                b.putString("ip", from);
                msg.setData(b);
                mHandler.sendMessage(msg);
            }

            @Override
            public void onClientStatusChanged(String client, int status) {
                Message msg = mHandler.obtainMessage(GET_CLIENT_STATUS);
                Bundle b = new Bundle();
                b.putString("client", client);
                b.putInt("status", status);
                msg.setData(b);
                mHandler.sendMessage(msg);
            }

            @Override
            public void onRemoteStatusChanged(String remote, int status) {
                Message msg = mHandler.obtainMessage(GET_CLIENT_STATUS);
                Bundle b = new Bundle();
                b.putString("remote", remote);
                b.putInt("status", status);
                msg.setData(b);
                mHandler.sendMessage(msg);
            }
        });

        mNetwork = new NetworkChecker(this);
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(TAG, "isconnected="+mNetwork.isConnected());
                if(mNetwork.isConnected()) {
                    NetworkChecker.NetworkConfig[] config = mNetwork.getIpInfo();
                    if (config != null) {
                        for (NetworkChecker.NetworkConfig c : config) {
                            mUDPBroadcast.setBlockIp(c.getIp());
                        }
                        if(mReceiver==null) {
                            if(!mUDPBroadcast.isReceiverEnabled()) {
                                Log.i(TAG, "start UDP Receiver");
                                mUDPBroadcast.enableReceiver(1000);
                            }
                            if(!mSocketConnector.isListening()) {
                                Log.i(TAG, "start tcp listener");
                                mSocketConnector.startListening(mTCPPort, 1000);
                            }
                        }
                    }
                } else {
                    if(mUDPBroadcast.isReceiverEnabled()) {
                        mUDPBroadcast.disableReceiver();
                    }
                    if(mSocketConnector.isListening()) {
                        try {
                            mSocketConnector.stopListening();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mBroadcastReceiver, filter);
    }

    @Override
    public void onDestroy() {
        // The service is no longer used and is being destroyed
        Log.i(TAG, "onDestroy");
        mThread.quitSafely();
        mEq.release();
        unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // All clients have unbound with unbindService()
        Log.i(TAG, "onUnbind");
        return true;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind");
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        Log.i(TAG, "onStartCommand");
        return START_STICKY;
    }

    private class LocalBinder extends EqualizerBinder {

        @Override
        public void enableEq(boolean enable) {
            mEq.enableEq(enable);
        }

        @Override
        public boolean adjustEq(int index, int adjust) {
            return mEq.adjustEq(index, adjust);
        }

        @Override
        public boolean getEqState() {
            return mEq.getEqState();
        }

        @Override
        public int getEqValue(int index) {
            return mEq.getEqValue(index);
        }

        @Override
        public int getEqFrequency(int index) {
            return mEq.getEqFrequency(index);
        }

    }


    private boolean parsePackage(SocketPackage p, int type) {
        if (p == null) {
            return false;
        }

        JSONObject[] request = p.getRequest();
        String ip = p.getSourceIp();
        int port = p.getSourcePort();

        if (request!=null && ip!=null && port!=0) {
            parseRequest(request, ip, port, type);
        }

        JSONObject[] response = p.getResponse();
        if (response != null) {
            parseResponse(response);
        }
        return true;
    }

    private void parseRequest(JSONObject[] request, String ip, int port, int type) {
        SocketPackage p = new SocketPackage();
        boolean needToSendBack = false;
        NetworkChecker.NetworkConfig[] ips = mNetwork.getIpInfo();
        if(ips == null) {
            return;
        }
        for(JSONObject s : request) {
            if(s == null) {
                continue;
            }
            String topic = null;
            if((topic =s.optString("topic")) != null) {
                boolean isItemAdded = false;
                JSONObject obj = new JSONObject();
                if(topic.equals("QueryHost")) {
                    JSONArray array = s.optJSONArray("item");
                    try {
                        obj.put("topic", "QueryHost");
                    } catch (JSONException e) {
                        e.printStackTrace();
                        break;
                    }
                    for(int i=0; i<array.length(); i++) {
                        String item = array.optString(i);
                        if(item==null) {
                            continue;
                        }
                        if(item.equals("ip")) {
                            for (NetworkChecker.NetworkConfig c : ips) {
                                if (c.isTheSameSubnet(ip)) {
                                    try {
                                        obj.put("ip", c.getIp());
                                        isItemAdded = true;
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                        break;
                                    }
                                }
                            }
                        } else if(item.equals("tcpport")) {
                            try {
                                obj.put("tcpport", mTCPPort);
                                isItemAdded = true;
                            } catch (JSONException e) {
                                e.printStackTrace();
                                break;
                            }
                        }
                    }
                } else if(topic.equals("EqState")) {
                    try {
                        obj.put("topic", "EqState");
                        obj.put("state", mEq.getEqState());
                        isItemAdded = true;
                    } catch (JSONException e) {
                        e.printStackTrace();
                        continue;
                    }
                } else if(topic.equals("EqValue")) {
                    int index = s.optInt("index", -1);
                    if(index!= -1) {
                        try {
                            obj.put("topic", "EqValue");
                            obj.put("index", index);
                            obj.put("value", mEq.getEqValue(index));
                            isItemAdded = true;
                        } catch (JSONException e) {
                            e.printStackTrace();
                            continue;
                        }
                    }
                } else if(topic.equals("EqFrequency")) {
                    int index = s.optInt("index", -1);
                    if(index!= -1) {
                        try {
                            obj.put("topic", "EqFrequency");
                            obj.put("index", index);
                            obj.put("frequency", mEq.getEqFrequency(index));
                            isItemAdded = true;
                        } catch (JSONException e) {
                            e.printStackTrace();
                            continue;
                        }
                    }
                }else if(topic.equals("TcpConnect")) {
                    String ip1 = s.optString("ip");
                    int port1 = s.optInt("tcpport",-1);
                    if(ip1!=null && port1!=-1) {
                        Message msg = mHandler.obtainMessage(CONNECT_TO_REMOTE);
                        Bundle b = new Bundle();
                        b.putString("ip", ip1);
                        b.putInt("port", port1);
                        msg.setData(b);
                        mHandler.sendMessage(msg);
                    }
                }
                if(isItemAdded) {
                    p.putResponse(obj);
                    needToSendBack = true;
                }
            }else {
                continue;
            }
        }

        if(needToSendBack) {
            if(type == UDP) {
                try {
                    mUDPBroadcast.sendData(p, ip, port);
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            }else if(type == TCP) {
                mSocketConnector.sendData(mSender, p);
            }
        }
    }

    private void parseResponse(JSONObject[] array) {
        for(JSONObject obj: array) {
            String topic = obj.optString("topic");
            if(topic == null) {
                continue;
            }
            if(topic.equals("EqValue")) {
                int index = obj.optInt("index", -(Equalizer.MAX_VALUE+1));
                int value = obj.optInt("value", -1);
                Log.i(TAG, "set value "+index+" to "+value);
                mEq.adjustEq(index, value);
            }else if(topic.equals("EqState")) {
                boolean state = obj.optBoolean("state", false);
                Log.i(TAG, "set state "+state);
                mEq.enableEq(state);
            }
        }
    }

}
