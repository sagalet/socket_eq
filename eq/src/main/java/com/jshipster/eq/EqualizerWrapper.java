package com.jshipster.eq;

/**
 * Created by davischen on 10/26/17.
 */

public abstract class EqualizerWrapper {
    public final static int EQ1 = 0x0;
    public final static int EQ2 = 0x1;
    public final static int EQ3 = 0x2;
    public final static int EQ4 = 0x3;
    public final static int EQ5 = 0x4;
    public final static int EQ6 = 0x5;
    public final static int EQ7 = 0x6;

    public final static int TATAL_EQ = 0x7;
    public final static int MAX_VALUE = 10;

    protected abstract boolean init();
    public abstract void release();
}
