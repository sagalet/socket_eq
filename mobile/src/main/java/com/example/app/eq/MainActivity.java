package com.example.app.eq;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Fragment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;

import com.example.eq.Equalizer;
import com.example.socket.NetworkChecker;
import com.example.socket.SocketConnector;
import com.example.socket.SocketPackage;
import com.example.socket.UDPBroadcast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.UnknownHostException;


public class MainActivity extends Activity {

    private String TAG = "Mobile_EQ";

    private DeviceFragment mDeviceFragment = null;
    private EqFragment mEqFragment = null;

    private UDPBroadcast mUDPBroadcast = null;
    private SocketConnector mSocketConnector = null;
    private Handler mHandler = null;
    private NetworkChecker mNetwork = null;

    private final int GET_UDP_DATA = 0x1;
    private final int GET_TCP_DATA = 0x2;
    private final int GET_CLIENT_STATUS = 0x4;
    private final int GET_REMOTE_STATUS = 0x8;
    private final int STOP_UDP_RECEIVER = 0x10;
    private final int CONNECT_TO_REMOTE = 0x20;
    private final int SEND_UDP_BROADCAST = 0x100;
    private final int TCP = 0x1000;
    private final int UDP = 0x2000;
    private int mUDPPort = 9010;
    private int mTCPPort = 9020;
    private boolean mScanning;
    private String mSender = null;
    private String mReceiver = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDeviceFragment = new DeviceFragment();
        mEqFragment = new EqFragment();

        int height = getValidHeight();
        int width = getValidWidth();
        mDeviceFragment.setValidHeight(height);
        mEqFragment.setValidHeight(width);

        mDeviceFragment.setOnClickListener(new DeviceFragment.OnClickListener() {
            @Override
            public void onItemClick(String name) {
                if(mUDPBroadcast.isReceiverEnabled()) {
                    mUDPBroadcast.disableReceiver();
                    mScanning = false;
                }
                String[] target = name.split(":");
                mSocketConnector.connect(target[0], Integer.parseInt(target[1]));
            }

            @Override
            public void onScanClick() {
                if(mUDPBroadcast != null) {
                    if(mUDPBroadcast.isReceiverEnabled()) {
                        mUDPBroadcast.disableReceiver();
                        mScanning = false;
                        mDeviceFragment.notifyScanning(false);
                    }else {
                        if(mNetwork.getIpInfo()==null) {
                            Toast.makeText(getApplicationContext(), "Network is not avaliable", Toast.LENGTH_LONG);
                            return;
                        }
                        mUDPBroadcast.enableReceiver(1000);
                        mScanning = true;
                        mHandler.sendMessageDelayed(mHandler.obtainMessage(STOP_UDP_RECEIVER), 10*1000);
                        SocketPackage p = new SocketPackage();
                        JSONObject j = new JSONObject();
                        try {
                            j.put("topic", "QueryHost");
                            JSONArray array = new JSONArray();
                            array.put("ip");
                            array.put("tcpport");
                            j.put("item", array);
                            p.putRequest(j);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            return;
                        }

                        Message m = mHandler.obtainMessage(SEND_UDP_BROADCAST);
                        Bundle b = new Bundle();
                        b.putSerializable("data", p);
                        m.setData(b);
                        mHandler.sendMessage(m);
                        mDeviceFragment.notifyScanning(true);
                    }
                }
            }
        });

        mEqFragment.setOnItemChangedListener(new EqFragment.OnItemChangedListener() {
            @Override
            public void onProgressChanged(int index, int value) {
                Log.i(TAG, "onProgressChanged mSender="+mSender+" idnex="+index+" value="+value);
                if (mSender != null) {
                    SocketPackage p = new SocketPackage();
                    JSONObject j = new JSONObject();
                    try {
                        j.put("topic", "EqValue");
                        j.put("index", index);
                        j.put("value", value);
                        p.putResponse(j);
                        if(mSender != null) {
                            mSocketConnector.sendData(mSender, p);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onSwitchChanged(boolean enable) {
                Log.i(TAG, "onSwitchChanged mSender="+mSender+" enable="+enable);
                SocketPackage p = new SocketPackage();
                JSONObject j = new JSONObject();
                try {
                    j.put("topic", "EqState");
                    j.put("state", enable);
                    p.putResponse(j);
                    if(mSender != null) {
                        mSocketConnector.sendData(mSender, p);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onDisconnectClick() {
                Log.i(TAG, "onDisconnectClick");
                mUDPBroadcast.release();
                mSocketConnector.release();
                switchFragment(mDeviceFragment);
            }
        });

        mHandler = new Handler(){
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
                        Log.i(TAG, "client="+client+" status="+status);
                        if(status == SocketConnector.STATUS_CONNECTED) {
                            mReceiver = client;
                            try {
                                mSocketConnector.stopListening();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            JSONObject obj = new JSONObject();
                            SocketPackage p = new SocketPackage();
                            try {
                                obj.put("topic", "EqState");
                                p.putRequest(obj);
                                for(int i = Equalizer.EQ1; i<Equalizer.TATAL_EQ; i++) {
                                    JSONObject j = new JSONObject();
                                    j.put("topic", "EqValue");
                                    j.put("index", i);
                                    p.putRequest(j);
                                }
                                mSocketConnector.sendData(mSender, p);

                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            switchFragment(mEqFragment);
                        } else {
                            mReceiver = null;
                        }
                        break;
                    }
                    case GET_REMOTE_STATUS: {
                        String remote = msg.getData().getString("remote");
                        int status = msg.getData().getInt("status");
                        Log.i(TAG, "remote="+remote+" status="+status);
                        if(status == SocketConnector.STATUS_CONNECTED) {
                            mSender = remote;
                            SocketPackage p = new SocketPackage();
                            JSONObject j = new JSONObject();
                            try {
                                j.put("topic", "TcpConnect");
                                String[] addr = remote.split(":");
                                NetworkChecker.NetworkConfig[] configs = mNetwork.getIpInfo();
                                for(NetworkChecker.NetworkConfig c : configs) {
                                    if(c.isTheSameSubnet(addr[0])) {
                                        j.put("ip", c.getIp());
                                    }
                                }
                                j.put("tcpport", mTCPPort);
                                p.putRequest(j);
                                mSocketConnector.startListening(mTCPPort,1000);
                                mSocketConnector.sendData(mSender, p);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }else if(status == SocketConnector.STATUS_DISCONNECTED){
                            mSender = null;
                        }
                        break;
                    }
                    case STOP_UDP_RECEIVER: {
                        if(mUDPBroadcast != null && mUDPBroadcast.isReceiverEnabled()) {
                            mScanning = false;
                            mUDPBroadcast.disableReceiver();
                            mDeviceFragment.notifyScanning(false);
                        }
                        break;
                    }
                    case SEND_UDP_BROADCAST: {
                        if(mUDPBroadcast != null && mScanning) {
                            SocketPackage p = (SocketPackage) msg.getData().getSerializable("data");
                            try {
                                NetworkChecker.NetworkConfig[] ips = mNetwork.getIpInfo();
                                for(NetworkChecker.NetworkConfig s : ips) {
                                    p.putSourceAddr(s.getIp(), mUDPPort);
                                    mUDPBroadcast.sendData(p, s.getBroadcastIp(), mUDPPort);
                                }
                            } catch (UnknownHostException e) {
                                e.printStackTrace();
                            }
                            Message newMsg = Message.obtain(msg);
                            mHandler.sendMessageDelayed(newMsg, 1000);
                        }
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
                Message msg = mHandler.obtainMessage(GET_UDP_DATA);
                msg.setData(b);
                mHandler.sendMessage(msg);
            }
        });

        mSocketConnector = new SocketConnector();

        mSocketConnector.setCallback(new SocketConnector.EventCallback() {
            @Override
            public void onDataReceive(String from, SocketPackage p) {
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
                Message msg = mHandler.obtainMessage(GET_REMOTE_STATUS);
                Bundle b = new Bundle();
                b.putString("remote", remote);
                b.putInt("status", status);
                msg.setData(b);
                mHandler.sendMessage(msg);
            }
        });

        mNetwork = new NetworkChecker(this);
        NetworkChecker.NetworkConfig[] config = mNetwork.getIpInfo();
        if(config == null) {
            Toast.makeText(this, "Network is not avaliable", Toast.LENGTH_LONG);
        } else {
            for(NetworkChecker.NetworkConfig c : config) {
                mUDPBroadcast.setBlockIp(c.getIp());
            }
        }

        mScanning = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.i(TAG, "onStop");
        if(mUDPBroadcast.isReceiverEnabled()) {
            mUDPBroadcast.disableReceiver();
        }
        if(mUDPBroadcast != null) {
            mUDPBroadcast.release();
        }

        if(mSocketConnector != null) {
            mSocketConnector.release();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.i(TAG, "onStart");
        switchFragment(mDeviceFragment);
    }

    private void switchFragment(Fragment fragment) {
        getFragmentManager().beginTransaction().
                replace(R.id.fragment_container, fragment).commit();
    }

    private int getValidHeight() {
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        return dm.heightPixels;
    }

    private int getValidWidth() {
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        return dm.widthPixels;
    }

    private boolean parsePackage(SocketPackage p, int type) {
        if (p == null) {
            return false;
        }

        JSONObject[] request = p.getRequest();
        String ip = p.getSourceIp();
        int port = p.getSourcePort();

        if (request!=null && (type==TCP || (ip!=null && port!=0))) {
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
                } else if(topic.equals("TcpConnect")) {
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
                mEqFragment.setEqValue(index, value);
            }else if(topic.equals("EqState")) {
                boolean state = obj.optBoolean("state", false);
                mEqFragment.setEqEnabled(state);
            } else if(topic.equals("QueryHost")) {
                String ip = obj.optString("ip");
                int port = obj.optInt("tcpport", -1);
                if(ip!=null && port!=-1) {
                    StringBuilder b = new StringBuilder().append(ip).
                            append(":").append(port);
                    mDeviceFragment.addItem(b.toString());
                }
            }
        }
    }

}
