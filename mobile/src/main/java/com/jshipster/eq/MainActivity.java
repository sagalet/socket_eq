package com.jshipster.eq;

import android.os.Bundle;

import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.jshipster.socket.NetworkChecker;
import com.jshipster.socket.SocketConnector;
import com.jshipster.socket.SocketPackage;
import com.jshipster.socket.UDPBroadcast;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.UnknownHostException;


public class MainActivity extends FragmentActivity {

    private String TAG = "eq_test";

    private DeviceFragment mDeviceFragment = null;
    private EqFragment mEqFragment = null;
    private Button mButton1 = null;
    private Button mButton2 = null;

    private UDPBroadcast mUDPBroadcast = null;
    private SocketConnector mSocketConnector = null;
    private Handler mHandler = null;
    private NetworkChecker mNetwork = null;

    private final int GET_UDP_DATA = 0x1;
    private final int GET_TCP_DATA = 0x2;
    private final int GET_CLIENT_STATUS = 0x4;
    private final int GET_REMOTE_STATUS = 0x8;
    private final int STOP_UDP_RECEIVER = 0x10;
    private final int SEND_UDP_BROADCAST =0x100;
    private int mUDPPort = 9010;
    private int mTCPPort = 9020;
    private boolean mScanning;
    private String mRemote = null;
    private String mClient = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDeviceFragment = new DeviceFragment();
        mEqFragment = new EqFragment();

        int height = getValidHeight();
        mDeviceFragment.setValidHeight(height);
        mEqFragment.setValidHeight(height);

        mDeviceFragment.setOnClickListener(new DeviceFragment.OnClickListener() {
            @Override
            public void onItemClick(String name) {
                String[] target = name.split(":");
                mSocketConnector.connect(target[0], Integer.parseInt(target[1]));
            }

            @Override
            public void onScanClick() {
                if(mUDPBroadcast != null) {
                    if(mUDPBroadcast.isReceiverEnabled()) {
                        mUDPBroadcast.disableReceiver();
                    }else {
                        if(mNetwork.getIpInfo()==null) {
                            Toast.makeText(getApplicationContext(), "Network is not avaliable", Toast.LENGTH_LONG);
                            return;
                        }
                        mUDPBroadcast.enableReceiver(1000);
                        mScanning = true;
                        mHandler.sendMessageDelayed(mHandler.obtainMessage(STOP_UDP_RECEIVER), 10*1000);
                        SocketPackage p = new SocketPackage();
                        p.putRequest("ip");
                        p.putRequest("port");
                        Message m = mHandler.obtainMessage(SEND_UDP_BROADCAST);
                        Bundle b = new Bundle();
                        b.putSerializable("data", p);
                        m.setData(b);
                        mHandler.sendMessage(m);
                    }
                }
            }
        });

        mEqFragment.setOnProgressChangedListener(new EqFragment.OnProgressChangedListener() {
            @Override
            public void onProgressChanged(int index, int value) {
                if (mRemote != null) {
                    SocketPackage p = new SocketPackage();
                    JSONObject j = new JSONObject();
                    try {
                        j.put("item", "eq" + index);
                        j.put("value", value);
                        p.putResponse(j);
                        mSocketConnector.sendData(mRemote, p);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
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
                        parseUDPPackage(p);
                        break;
                    }
                    case GET_TCP_DATA: {
                        break;
                    }
                    case GET_CLIENT_STATUS: {

                        break;
                    }
                    case GET_REMOTE_STATUS: {
                        break;
                    }
                    case STOP_UDP_RECEIVER: {
                        if(mUDPBroadcast != null && mUDPBroadcast.isReceiverEnabled()) {
                            mScanning = false;
                            mUDPBroadcast.disableReceiver();
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

            }

            @Override
            public void onClientStatusChanged(String client, int status) {

            }

            @Override
            public void onRemoteStatusChanged(String remote, int status) {
                Log.e(TAG,"connect to "+remote+" status="+status);
                if(status == SocketConnector.STATUS_CONNECTED) {
                    mRemote = remote;
                }else if(status == SocketConnector.STATUS_DISCONNECTED){
                    mRemote = null;
                }
            }
        });

        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_container, mDeviceFragment)
                .commit();

        mButton1 = findViewById(R.id.button1);
        mButton1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getSupportFragmentManager().beginTransaction().
                        replace(R.id.fragment_container, mEqFragment).commit();
            }
        });

        mButton2 = findViewById(R.id.button2);
        mButton2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getSupportFragmentManager().beginTransaction().
                        replace(R.id.fragment_container, mDeviceFragment).commit();
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
        Log.e(TAG, "onDestroy");
        if(mUDPBroadcast.isReceiverEnabled()) {
            mUDPBroadcast.disableReceiver();
        }
        if(mRemote != null) {
            mSocketConnector.disconnect(mRemote);
        }
        if(mClient != null) {
            mSocketConnector.disconnect(mClient);
        }
    }

    private int getValidHeight() {
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        LinearLayout header = findViewById(R.id.llo_header);
        header.measure(0, 0);
        return dm.heightPixels - header.getMeasuredHeight();
    }

    private boolean parseUDPPackage(SocketPackage p) {
        if (p == null) {
            return false;
        }

        String[] request = p.getRequest();
        String ip = p.getSourceIp();
        int port = p.getSourcePort();

        if (request!=null && ip!=null && port!=0) {
            parseUDPRequest(request, ip, port);
        }

        JSONObject[] response = p.getResponse();
        if (response != null) {
            Log.e(TAG, "start parse response");
            parseUDPResponse(response);
        }
        return true;
    }

    private void parseUDPRequest(String[] request, String ip, int port) {
        SocketPackage p = new SocketPackage();
        boolean needToSendBack = false;
        NetworkChecker.NetworkConfig[] ips = mNetwork.getIpInfo();
        if(ips == null) {
            return;
        }
        for(String s : request) {
            if(s.equals("ip")) {
                for(NetworkChecker.NetworkConfig c : ips) {
                    if(c.isTheSameSubnet(ip)) {
                        JSONObject j = new JSONObject();
                        try {
                            j.put("item", "ip");
                            j.put("value", c.getIp());
                            p.putResponse(j);
                            needToSendBack = true;
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }else if(s.equals("port")) {
                JSONObject j = new JSONObject();
                try {
                    j.put("item", "tcpport");
                    j.put("value", mTCPPort);
                    p.putResponse(j);
                    needToSendBack = true;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        if(needToSendBack) {
            try {
                mUDPBroadcast.sendData(p, ip, port);
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }
    }

    private void parseUDPResponse(JSONObject[] array) {
        String ip = null;
        String port = null;
        for(JSONObject j : array) {
            String item = j.optString("item");
            if(item == null) {
                continue;
            }
            String value = j.optString("value");
            if(value == null) {
                continue;
            }
            if(item.equals("ip")) {
                ip = value;
            }else if (item.equals("tcpport")) {
                port = value;
            }
        }
        Log.e(TAG, "get "+ip+" : "+port);
        if(ip !=null && port !=null) {
            Log.e(TAG, "get device "+ip+":"+port);
            mDeviceFragment.addItem(ip+":"+port);
        }
    }

}
