package com.jshipster.eq;

public class Equalizer extends EqualizerWrapper implements EqualizerInterface{
    private EqualizerImpl mImpl = null;
    private static Equalizer mEq = null;
    
    private Equalizer() {

    }
    
    public static Equalizer getEqualizer() {
        if(mEq == null) {
            mEq = new Equalizer();
        }
        mEq.init();
        return mEq;
    }

    @Override
    protected boolean init() {
        if(mImpl==null) {
            mImpl = EqualizerImpl.getEqualizer();
        }
        return mImpl.init();
    }
    
    @Override
    public void enableEq(boolean enable) {
        mImpl.enableEq(enable);
    }
    
    @Override
    public boolean adjustEq(int index, int adjust) {
        return mImpl.adjustEq(index, adjust);
    }
    
    @Override
    public void release() {
        mImpl.release();
    }
    
    @Override
    public boolean getEqState() {
        return mImpl.getEqState();
    }
    
    @Override
    public int getEqValue(int index) {
        return mImpl.getEqValue(index);
    }
    
    @Override
    public int getEqFrequency(int index) {
        return mImpl.getEqFrequency(index);
    }
}
