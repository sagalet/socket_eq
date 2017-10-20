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

import java.io.IOException;
import java.net.UnknownHostException;


public class MainActivity extends FragmentActivity {

    private String TAG = "eq_test";

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

    private String mClient = null;
    private String mRemote = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        mEqFragment = new EqFragment();

        int height = getValidHeight();

        mEqFragment.setValidHeight(height);

        mEqFragment.setOnProgressChangedListener(new EqFragment.OnProgressChangedListener() {
            @Override
            public void onProgressChanged(int index, int value) {

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
                        String client = msg.getData().getString("client");
                        int status = msg.getData().getInt("status");
                        if(status == SocketConnector.STATUS_CONNECTED) {
                            mClient = client;
                            mUDPBroadcast.disableReceiver();
                            try {
                                mSocketConnector.stopListening();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else if(status == SocketConnector.STATUS_DISCONNECTED) {
                            mClient = null;
                            mUDPBroadcast.enableReceiver(1000);
                            mSocketConnector.startListening(mTCPPort, 1000);
                        }
                        break;
                    }
                    case GET_REMOTE_STATUS: {

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

                            mHandler.sendMessageDelayed(msg, 1000);
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
                Log.i(TAG,"receive from "+from+" data="+p.getObj().toString());
            }

            @Override
            public void onClientStatusChanged(String client, int status) {
                Message msg = mHandler.obtainMessage(GET_CLIENT_STATUS);
                Bundle b = new Bundle();
                b.putString("client", client);
                b.putInt("status", status);
                mHandler.sendMessage(msg);
            }

            @Override
            public void onRemoteStatusChanged(String remote, int status) {

            }
        });



        mButton1 = findViewById(R.id.button1);
        mButton1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mUDPBroadcast.enableReceiver(mUDPPort);
                mSocketConnector.startListening(mTCPPort, 1000);
            }
        });

        mButton2 = findViewById(R.id.button2);
        mButton2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mUDPBroadcast.disableReceiver();
                try {
                    mSocketConnector.stopListening();
                } catch (IOException e) {
                    e.printStackTrace();
                }
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

    }

}
