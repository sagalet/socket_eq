package com.jshipster.socket;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

public abstract class StopableThread implements Runnable {
    private boolean mStop = true;
    private HandlerThread mHandlerThread = null;
    private Handler mHandler = null;
    private String mName = null;

    protected abstract boolean preStop();
    protected abstract void postStop();
    protected abstract boolean preLoop();
    protected abstract void postLoop();
    protected abstract boolean loop() throws Exception;
    protected abstract void onExceptionFired(Exception e);

    protected void setName(String name) {
        mName = name;
    }

    protected String getName(String name) {
        return name;
    }

    private synchronized void setStop(boolean stop) {
        mStop = stop;
    };

    public void start() {
        if(mHandlerThread == null) {
            mHandlerThread = new HandlerThread(mName);
            mHandlerThread.start();
        }

        if(mHandler==null) {
            mHandler = new Handler(mHandlerThread.getLooper());
        }

        if(isStop()) {
            mHandler.post(this);
        } else {
            Log.i(mName, mName+" is running");
        }
    }

    public void tryStop() {
        if(preStop()) {
            setStop(true);
        }
        postStop();
    }

    public boolean isStop() {
        return mStop;
    }

    @Override
    public void run() {
        if(preLoop()) {
            setStop(false);
            try {
                while (!mStop) {
                    if(!loop()) {
                        tryStop();
                    }
                }
            } catch (Exception e) {
                onExceptionFired(e);
                tryStop();
            }
        }
        postLoop();
    }
}
