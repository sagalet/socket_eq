package com.example.app.eq;

import android.content.Context;
import android.os.Bundle;
import android.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.Switch;

import java.util.ArrayList;

public class EqFragment extends Fragment implements SeekBar.OnSeekBarChangeListener, View.OnClickListener {

    private String TAG = "EqFragment";
    private final int TOTAL_EQS = 7;
    private final int MAX_VALUE = 10;
    private int mSeekBarId[] = {
            R.id.seekBar1,
            R.id.seekBar2,
            R.id.seekBar3,
            R.id.seekBar4,
            R.id.seekBar5,
            R.id.seekBar6,
            R.id.seekBar7,
            //R.id.seekBar8,
            //R.id.seekBar9,
            //R.id.seekBar10,
    };

    private int mEqValues[];
    private int mValidHeight = 0;
    private ArrayList<OnItemChangedListener> mListener = null;
    private Context mContext = null;
    private View mView = null;
    private boolean mEnabled = false;

    public EqFragment() {
        mListener = new ArrayList<>();
        mEqValues = new int[TOTAL_EQS];
        for(int i=0; i<TOTAL_EQS; i++) {
            mEqValues[i] = 10;
        }
    }

    @Override
    public void onAttach (Context context) {
        super.onAttach(context);
        mContext = context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_eq, container, false);
        SeekBar[] seekBar = new SeekBar[TOTAL_EQS];
        for(int i=0; i<TOTAL_EQS; i++) {
            seekBar[i] = v.findViewById(mSeekBarId[i]);
        }
        seekBar[0].measure(0, 0);
        int heightSeekBar = seekBar[0].getMeasuredHeight();
        int heightHeader = v.findViewById(R.id.rly_header).getHeight();
        //Log.i(TAG, "valid="+mValidHeight+" seekbar="+heightSeekBar);
        int margin = mValidHeight/(TOTAL_EQS+TOTAL_EQS/2) - heightSeekBar - heightHeader;
        if(margin > 0) {
            for(int i=0; i< TOTAL_EQS; i++) {
                SeekBar s = seekBar[i];
                s.setPadding(s.getPaddingLeft(), margin, s.getPaddingRight(), s.getPaddingBottom());
                s.setVisibility(View.VISIBLE);
                s.setProgress(mEqValues[i]);
                s.setOnSeekBarChangeListener(this);
                s.setEnabled(mEnabled);
            }
        }

        Switch swt = v.findViewById(R.id.swt_eq_enable);
        swt.setChecked(mEnabled);
        swt.setOnClickListener(this);
        return v;
    }

    @Override
    public void onActivityCreated (Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        View v= getView();
        //Log.i(TAG,"v="+v+" enable="+mEnabled);
        setEqEnabled(mEnabled);
    }

    public void setValidHeight(int height) {
        mValidHeight = height;
    }

    public void setEqTag(int index,String tag) {

    }

    public void setEqValue(int index, int value) {
        if(index>=TOTAL_EQS || index<0) {
            Log.e(TAG,"index is out of range");
            return;
        }

        if(value>MAX_VALUE || value<-MAX_VALUE) {
            Log.e(TAG,"value is out of range", new Throwable());
            return;
        }
        mEqValues[index] = value+10;
        View v = getView();
        //Log.i(TAG, "v="+v+" setEqValue="+index+" "+value);
        if(v != null) {
            SeekBar s = v.findViewById(mSeekBarId[index]);
            s.setProgress(value+10);
        }
    }

    public void setEqEnabled(boolean enable) {
        View v = getView();
        Log.i(TAG,"v="+v+" setEqEnabled="+mEnabled+" to "+enable);
        if(mEnabled != enable) {
            mEnabled = enable;
            if (v != null) {
                Switch swt = (Switch) v.findViewById(R.id.swt_eq_enable);

                swt.setChecked(enable);
                for (int i = 0; i < TOTAL_EQS; i++) {
                    SeekBar bar = v.findViewById(mSeekBarId[i]);
                    bar.setEnabled(enable);
                }
            }
        }
    }


    public void setOnItemChangedListener(OnItemChangedListener l) {
        mListener.add(l);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
        int index = getIndex(seekBar.getId());
        Log.i(TAG, "seekbar changed index="+index+" i="+i);
        if(index >= 0) {
            mEqValues[index] = i;
            for(OnItemChangedListener l : mListener) {
                l.onProgressChanged(index, i-10);
            }
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onClick(View view) {
        Log.i(TAG, "switch onClick");
        Switch swt = view.findViewById(R.id.swt_eq_enable);
        if(swt == null) {
            Log.e(TAG,"get switch failed");
            return;
        }

        setEqEnabled(swt.isChecked());
        for(OnItemChangedListener listen : mListener) {
            listen.onSwitchChanged(swt.isChecked());
        }

    }

    public interface OnItemChangedListener {
        void onProgressChanged(int index, int value);
        void onSwitchChanged(boolean enable);
    }

    private int getIndex(int id) {
        for(int i=0; i<TOTAL_EQS; i++) {
            if(mSeekBarId[i] == id) {
                return i;
            }
        }
        return -1;
    }
}
