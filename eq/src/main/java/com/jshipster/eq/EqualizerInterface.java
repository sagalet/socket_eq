package com.jshipster.eq;

/**
 * Created by davischen on 10/26/17.
 */

public interface EqualizerInterface {
    void enableEq(boolean enable);
    boolean adjustEq(int index, int adjust);
    boolean getEqState();
    int getEqValue(int index);
    int getEqFrequency(int index);
}
