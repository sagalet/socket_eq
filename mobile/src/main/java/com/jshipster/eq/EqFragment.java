package com.jshipster.eq;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.Toast;

import java.util.ArrayList;

public class EqFragment extends Fragment implements SeekBar.OnSeekBarChangeListener{

    private String TAG = "EqFragment";
    private final int TOTAL_EQS = 7;
    private int mSeekBarId[] = {
            R.id.seekBar1,
            R.id.seekBar2,
            R.id.seekBar3,
            R.id.seekBar4,
            R.id.seekBar5,
            R.id.seekBar6,
            R.id.seekBar7,
    };

    private int mEqValues[];
    private int mValidHeight = 0;
    private ArrayList<OnProgressChangedListener> mListener = null;
    private Context mContext = null;

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
        //Log.i(TAG, "valid="+mValidHeight+" layout="+heightLayout+" seekbar="+heightSeekBar);
        int margin = mValidHeight/(TOTAL_EQS+TOTAL_EQS/2) - heightSeekBar;
        if(margin > 0) {
            for(SeekBar s : seekBar) {
                s.setPadding(s.getPaddingLeft(), margin, s.getPaddingRight(), s.getPaddingBottom());
                s.setOnSeekBarChangeListener(this);
            }
        }

        return v;
    }

    public void setValidHeight(int height) {
        mValidHeight = height;
    }

    public void setOnProgressChangedListener(OnProgressChangedListener l) {
        mListener.add(l);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
        int index = getIndex(seekBar.getId());
        if(index>=0) {
            mEqValues[index] = i;
            for(OnProgressChangedListener l : mListener) {
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

    public interface OnProgressChangedListener {
        void onProgressChanged(int index, int value);
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
