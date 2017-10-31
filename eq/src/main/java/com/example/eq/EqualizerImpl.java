package com.example.eq;
import android.util.Log;

public class EqualizerImpl extends EqualizerWrapper implements EqualizerInterface {
    private final String TAG = "Equalizer";

    private boolean mInit = false;
    private static EqualizerImpl mEq;

    private EqualizerImpl() {

    }

    @Override
    protected boolean init() {
        if(mInit) {
            return true;
        }
        mInit = nativeInit();
        if(!mInit) {
            Log.e(TAG, "native init failed");
        }
        return mInit;
    }

    public static EqualizerImpl getEqualizer() {
        if(mEq == null) {
            mEq = new EqualizerImpl();
        }
        mEq.init();
        return mEq;
    }

    @Override
    public void enableEq(boolean enable) {
        if(mInit) {
            nativeEnableEq(enable);
        }
    }

    @Override
    public boolean adjustEq(int index, int adjust) {
        if(mInit) {
            return nativeAdjustEq(index, adjust);
        }
        return false;
    }

    @Override
    public void release() {
        if(mInit) {
            nativeRelease();
            mInit = false;
        }
    }

    @Override
    public boolean getEqState() {
        if(mInit) {
            return nativeGetEqState();
        }
        return false;
    }

    @Override
    public int getEqValue(int index) {
        if(mInit) {
            return nativeGetEqValue(index);
        }
        return -(MAX_VALUE+1);
    }

    @Override
    public int getEqFrequency(int index) {
        if(mInit) {
            return nativeGetEqFrequency(index);
        }
        return -1;
    }

    private native boolean nativeInit();
    private native void nativeEnableEq(boolean enable);
    private native boolean nativeAdjustEq(int index, int adjust);
    private native void nativeRelease();
    private native boolean nativeGetEqState();
    private native int nativeGetEqValue(int index);
    private native int nativeGetEqFrequency(int index);

    static {
        System.loadLibrary("jsequalizer");
    }
}
