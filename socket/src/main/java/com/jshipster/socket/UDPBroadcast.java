package com.jshipster.socket;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;

public class UDPBroadcast {

    private final int UDP_RECEIVE = 0x1;
    private final int UDP_SEND = 0x2;

    private String TAG = "UDPBroadcast";

    private Handler mHander = null;

    private InnerReceiver mReceiver = null;
    private InnerSender mSender = null;
    private int mPort;
    private String mAddr;
    private ArrayList<EventCallback> mCallbacks = null;
    private ArrayList<String> mBlockIp = null;

    public UDPBroadcast(int port) {
        mPort = port;
        mHander = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case UDP_RECEIVE: {
                        byte[] data = msg.getData().getByteArray("data");
                        String ip = msg.getData().getString("ip");
                        SocketPackage p = SocketPackage.convertToSocketPackage(data);
                        if (p != null) {
                            for (EventCallback e : mCallbacks) {
                                Log.e(TAG, "call onDataReceive");
                                e.onDataReceive(ip, p);
                            }
                        }else {
                            Log.e(TAG, "p is null");
                        }
                        break;
                    }
                    case UDP_SEND:
                        break;
                }
            }
        };

        mCallbacks = new ArrayList<>();
        mBlockIp = new ArrayList<>();
    }

    public void enableReceiver(int timeout) {
        if (mReceiver == null) {
            mReceiver = new InnerReceiver(mPort, mHander);
        }

        if (mReceiver.isStop()) {
            mReceiver.setTimeout(timeout);
            mReceiver.start();
        }else {
            Log.w(TAG,"UDP receiver is already enabled");
        }
    }

    public void disableReceiver() {

        if(mReceiver==null || !mReceiver.isStop()) {
            mReceiver.tryStop();
        }else {
            Log.w(TAG,"UDP receiver is not enabled");
        }
    }

    public boolean isReceiverEnabled() {
        if(mReceiver!= null) {
            return !mReceiver.isStop();
        }
        return false;
    }

    public void sendData(SocketPackage p) throws UnknownHostException {
        sendData(p, mAddr, mPort);
    }

    public void sendData(SocketPackage p, String addr, int port) throws UnknownHostException {
        if(mSender == null) {
            mSender = new InnerSender(port, addr, mHander);
        } else {
            mSender.setTarget(port, addr);
        }
        mSender.sendData(p);
    }

    public void setCallback(EventCallback callback) {
        mCallbacks.add(callback);
    }

    public void setBlockIp(String ip) {
        mBlockIp.add(ip);
    }

    public interface EventCallback {
        void onDataReceive(String ip, SocketPackage data);
    }

    private class InnerReceiver extends StopableThread {
        private int mTimeout;
        private int mPort;
        private Handler mHandler = null;
        DatagramSocket mSocket = null;

        public InnerReceiver(int port, Handler handler) {
            this(port, 0, handler);
        }

        public InnerReceiver(int port, int timeout, Handler handler) {
            mPort = port;
            mTimeout = timeout;
            mHandler = handler;
            setName("Receiver:"+port);
        }

        public boolean setTimeout(int timeout){
            if (isStop()) {
                mTimeout = timeout;
                return true;
            } else {
                Log.e(TAG, "Do not set timeout when receiver is enabled. Stop it first");
                return false;
            }
        }

        @Override
        protected boolean preStop() {
            return true;
        }

        @Override
        protected void postStop() {

        }

        @Override
        protected boolean preLoop() {
            try {
                mSocket = new DatagramSocket(mPort);
                mSocket.setBroadcast(true);
                mSocket.setSoTimeout(mTimeout);
            }catch (SocketException e) {
                e.printStackTrace();
                return false;
            }
            return true;
        }

        @Override
        protected void postLoop() {
            if(mSocket!=null) {
                mSocket.close();
                mSocket = null;
            }
        }

        @Override
        protected boolean loop() throws Exception {
            //Log.i(TAG,"start UDP receive");
            byte[] data = new byte[1024];
            DatagramPacket packet = new DatagramPacket(data, data.length);
            try {
                mSocket.receive(packet);
                if(mBlockIp.contains(packet.getAddress().getHostAddress())) {
                    Log.e(TAG, "receive from block ip "+packet.getAddress().getHostAddress());
                }else {
                    if (packet.getLength() > 0) {
                        byte[] buf = Arrays.copyOf(packet.getData(), packet.getLength());
                        Message msg = mHandler.obtainMessage(UDP_RECEIVE);
                        Bundle b = new Bundle();
                        Log.i(TAG, " receive " + packet.getData().length + " " + new String(packet.getData()).trim());
                        b.putByteArray("data", buf);
                        b.putString("ip", packet.getAddress().getHostAddress());
                        msg.setData(b);
                        mHandler.sendMessage(msg);
                    }
                }
            } catch (SocketTimeoutException e) {
                //do nothing
            }
            return true;
        }

        @Override
        protected void onExceptionFired(Exception e) {
            e.printStackTrace();
        }
    }

    private class InnerSender implements Runnable {
        private int mPort;
        private InetAddress mAddr = null;
        private Handler mHandler = null;
        private byte[] mData;

        public InnerSender(int port, String addr, Handler handler) throws UnknownHostException {
            mHander = handler;
            setTarget(port, addr);
        }

        @Override
        public void run() {
            if(mData==null) {
                Log.e(TAG, "Data is empty");
                return;
            }
            Log.i(TAG,"send "+mData.length+" "+new String(mData));
            DatagramPacket packet = new DatagramPacket(mData, mData.length, mAddr, mPort);
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket();
                socket.send(packet);
            } catch (SocketException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if(socket!=null) {
                socket.close();
            }
            mData = null;
        }

        public void setTarget(int port, String addr) throws UnknownHostException {
            mPort = port;
            mAddr = InetAddress.getByName(addr);
        }

        public void sendData(SocketPackage data) {
            mData = SocketPackage.convertToByteArray(data);
            Thread thread = new Thread(this);
            thread.start();
        }
    }
}