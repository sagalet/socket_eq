package com.jshipster.socket;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public class SocketConnector {

    private String TAG = "SocketConnecter";
    private HashMap<String, Socket> mRemotes = null;
    private HashMap<String , Pair<Socket, InnerSocketHandler>> mClients = null;
    private ArrayList<EventCallback> mCallbacks = null;
    private InnerSocketListener mSocketListener = null;
    private HandlerThread mThread = null;

    private final int GET_CLIENT_SOCKET = 0x1;
    private final int GET_CLIENT_DATA = 0x2;
    private final int REMOVE_CLIENT_SOCKET = 0x4;
    private final int CONNECT_REMOTE = 0x8;
    private final int DISCONNECT_REMOTE = 0x10;
    private Handler mHandler = null;

    public final static int STATUS_CONNECTED = 0x100;
    public final static int STATUS_DISCONNECTED = 0x200;
    public final static int STATUS_CONNECT_FAILED = 0x400;


    public SocketConnector() {
        mRemotes = new HashMap<>();
        mClients = new HashMap<>();
        mThread = new HandlerThread("SocketConnector");
        mThread.start();
        mHandler = new Handler(mThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch(msg.what) {
                    case GET_CLIENT_SOCKET: {
                        Socket socket = (Socket) msg.obj;
                        try {
                            socket.shutdownOutput();
                        } catch (IOException e) {
                            e.printStackTrace();
                            return;
                        }
                        String name = obtainName(socket);
                        InnerSocketHandler handler = new InnerSocketHandler(socket, name, mHandler);
                        mClients.put(name, new Pair<>(socket, handler));
                        for (EventCallback e : mCallbacks) {
                            e.onClientStatusChanged(name, STATUS_CONNECTED);
                        }
                        handler.start();
                        break;
                    }
                    case GET_CLIENT_DATA: {
                        String name = msg.getData().getString("name");
                        byte[] data = msg.getData().getByteArray("data");
                        SocketPackage p = SocketPackage.convertToSocketPackage(data);
                        if(p!=null) {
                            for (EventCallback e : mCallbacks) {
                                e.onDataReceive(name, p);
                            }
                        }
                        break;
                    }
                    case REMOVE_CLIENT_SOCKET: {
                        String name = msg.getData().getString("name");
                        Pair<Socket, InnerSocketHandler> p = mClients.remove(name);
                        if (p != null) {
                            try {
                                p.second.tryStop();
                                p.first.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                        }
                        break;
                    }
                    case CONNECT_REMOTE: {
                        String ip = msg.getData().getString("ip");
                        int port = msg.getData().getInt("port");
                        String name = obtainName(ip, port);
                        int status = STATUS_DISCONNECTED;
                        if(mClients.containsKey(name)) {
                            Log.w(TAG, name+" is connected");
                            return;
                        }
                        SocketAddress addr = new InetSocketAddress(ip, port);
                        try {
                            Socket socket = new Socket();
                            socket.connect(addr, 5000);
                            socket.shutdownInput();
                            mRemotes.put(name, socket);
                            status = STATUS_CONNECTED;
                        } catch (SocketException e) {
                            e.printStackTrace();
                        } catch (SocketTimeoutException e) {
                            Log.e(TAG, "connect to "+ip+":"+port+" timeout");
                            status = STATUS_CONNECT_FAILED;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        for (EventCallback e : mCallbacks) {
                            e.onRemoteStatusChanged(name, status);
                        }
                        break;
                    }
                    case DISCONNECT_REMOTE: {
                        String name = msg.getData().getString("name");
                        Socket socket = mRemotes.remove(name);
                        if(socket != null) {
                            try {
                                socket.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            for (EventCallback e : mCallbacks) {
                                e.onRemoteStatusChanged(name, STATUS_DISCONNECTED);
                            }
                        } else {
                            Log.w(TAG, name+" is not connected");
                        }
                        break;
                    }
                }
            }
        };
    }

    public void startListening(int port, int timeout) {

        if(mSocketListener == null) {
            mSocketListener = new InnerSocketListener(port, timeout, mHandler);
        }

        if(mSocketListener.isStop()) {
            mSocketListener.init(port, timeout);
            Log.i(TAG,"start listener "+port);
            mSocketListener.start();
        }else {
            Log.w(TAG,"Server socket is listening");
        }
    }

    public void stopListening() throws IOException {
        if(mSocketListener != null) {
            mSocketListener.tryStop();
        }else {
            Log.w(TAG,"socket listener is not listening");
        }
    }

    public void connect(String ip, int port) {
        Message msg = mHandler.obtainMessage(CONNECT_REMOTE);
        Bundle b = new Bundle();
        b.putString("ip", ip);
        b.putInt("port", port);
        msg.setData(b);
        mHandler.sendMessage(msg);
    }

    public void disconnect(String name) {
        Message msg = mHandler.obtainMessage(DISCONNECT_REMOTE);
        Bundle b = new Bundle();
        b.putString("name", name);
        msg.setData(b);
        mHandler.sendMessage(msg);
    }

    public void sendData(String name, SocketPackage p) {
        Socket socket = mRemotes.get(name);
        if(socket != null) {
            Thread thread = new Thread(new InnerSocketSender(
                    SocketPackage.convertToByteArray(p), socket));
            thread.start();
        } else {
            Log.e(TAG, name+" is not connected");
        }
    }

    public void setCallback(EventCallback e) {
        if(mCallbacks == null) {
            mCallbacks = new ArrayList<>();
        }
        mCallbacks.add(e);
    }

    public interface EventCallback {
        void onDataReceive(String from, SocketPackage p);
        void onClientStatusChanged(String client, int status);
        void onRemoteStatusChanged(String remote,int status);
    }

    public boolean isListening() {
        if(mSocketListener != null) {
            return mSocketListener.isStop();
        }
        return false;
    }

    public void release(){
        if(isListening()) {
            try {
                stopListening();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if(!mRemotes.isEmpty()) {
            Set<String> set = mRemotes.keySet();
            for(String s : set) {
                Socket socket = mRemotes.remove(s);
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        if(!mClients.isEmpty()) {
            Set<String> set = mClients.keySet();
            for(String s : set) {
                Pair<Socket, InnerSocketHandler> p = mClients.remove(s);
                p.second.tryStop();
            }
        }
    }

    private String obtainName(String ip, int port) {
        String name = new StringBuilder().
                append(ip).
                append(":").append(port).
                toString();
        return name;
    }

    private String obtainName(Socket socket) {
        return obtainName(socket.getInetAddress().getHostAddress(),
                socket.getPort());
    }

    private class InnerSocketListener extends StopableThread {

        private int mPort;
        private int mTimeout;
        private ServerSocket mServer = null;
        private Handler mOuterHandler = null;

        public InnerSocketListener(int port, int timeout, Handler handler) {
            mOuterHandler = handler;
            init(port, timeout);
            setName("Listener:"+port);
        }

        public void init(int port, int timeout) {
            mPort = port;
            mTimeout = timeout;
        }

        @Override
        protected boolean preStop() {
            if(mTimeout == 0 && mServer != null) {
                try {
                    mServer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return true;
        }

        @Override
        protected void postStop() {

        }

        @Override
        protected boolean preLoop() {
            try {
                mServer = new ServerSocket(mPort);
                mServer.setSoTimeout(mTimeout);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }

            return true;
        }

        @Override
        protected void postLoop() {
            if(mServer!=null) {
                try {
                    mServer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mServer = null;
            }
        }

        @Override
        protected boolean loop() throws Exception {
            try {
                Socket socket = mServer.accept();
                Log.i(TAG, "accept from "+socket.getInetAddress().getHostAddress());
                Message msg = mOuterHandler.obtainMessage(GET_CLIENT_SOCKET);
                msg.obj = socket;
                mOuterHandler.sendMessage(msg);
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

    private class InnerSocketHandler extends StopableThread {
        private Socket mSocket = null;
        private Handler mOuterHandler = null;
        private String mName = null;

        public InnerSocketHandler(Socket socket, String name, Handler handler) {
            mSocket = socket;
            mName = name;
            mOuterHandler = handler;
            setName("Handler:"+name);
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
            Log.i(TAG, " socket handler preloop");
            try {
                mSocket.setSoTimeout(1000);
            } catch (SocketException e) {
                e.printStackTrace();
                return false;
            }
            return true;
        }

        @Override
        protected void postLoop() {
            if(mSocket!=null) {
                try {
                    mSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Message msg = mOuterHandler.obtainMessage(REMOVE_CLIENT_SOCKET);
                Bundle b = new Bundle();
                b.putString("name", mName);
                msg.obj = mSocket;
                msg.setData(b);
                mOuterHandler.sendMessage(msg);
            }
        }

        @Override
        protected boolean loop() throws Exception {
            byte[] data = new byte[1024];
            try {
                int size = mSocket.getInputStream().read(data, 0, data.length);
                if (size > 0) {
                    Message msg = mOuterHandler.obtainMessage(GET_CLIENT_DATA);
                    Bundle b = new Bundle();
                    Log.i(TAG," receive data="+new String(data).trim());
                    b.putByteArray("data", data);
                    b.putString("name", mName);
                    msg.setData(b);
                    mOuterHandler.sendMessage(msg);
                }else {
                    Log.i(TAG," receive size="+size);
                    return false;
                }
            }catch (SocketTimeoutException e) {
                //Log.i(TAG,"receive timeout");
            }
            return true;
        }

        @Override
        protected void onExceptionFired(Exception e) {
            e.printStackTrace();
        }
    }

    private class InnerSocketSender implements Runnable {

        private byte[] mData;
        private Socket mSocket;

        public InnerSocketSender(byte[] data, Socket socket) {
            mData = data;
            mSocket = socket;
        }

        @Override
        public void run() {
            try {
                OutputStream out = mSocket.getOutputStream();
                Log.i(TAG,"try send data="+new String(mData).trim());
                out.write(mData);
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
