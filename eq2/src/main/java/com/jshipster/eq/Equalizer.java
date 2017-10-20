package com.jshipster.eq;

public class Equalizer {
    private boolean status = false;

    public void enableEq(boolean enable) {
        nativeEnableEq(enable);
    }

    public void adjustEq(int index, int value) {
        nativeAdjustEq(index, value);
    }

    private native void nativeEnableEq(boolean enable);
    private native void nativeAdjustEq(int index, int value);
}
