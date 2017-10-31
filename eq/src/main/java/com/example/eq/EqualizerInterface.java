package com.example.eq;


public interface EqualizerInterface {
    void enableEq(boolean enable);
    boolean adjustEq(int index, int adjust);
    boolean getEqState();
    int getEqValue(int index);
    int getEqFrequency(int index);
}
