package com.example.app.eq;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

import android.util.DisplayMetrics;
import android.util.Log;

import com.example.app.eq.eq.EqualizerBinder;
import com.example.app.eq.eq.EqualizerService;
import com.example.eq.Equalizer;

public class MainActivity extends Activity {

    private String TAG = "eq_test";
    private EqFragment mEqFragment = null;
    private EqualizerBinder mBinder = null;
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mBinder = (EqualizerBinder) iBinder;
            Log.i(TAG, "onServiceConnected name="+componentName);

            mEqFragment.setEqEnabled(mBinder.getEqState());
            for(int i=Equalizer.EQ1; i<Equalizer.TATAL_EQ; i++) {
                mEqFragment.setEqTag(i, new StringBuilder().append(mBinder.getEqFrequency(i)).toString());
                mEqFragment.setEqValue(i, mBinder.getEqValue(i));
            }

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBinder = null;
            Log.i(TAG, "onServiceDisconnected name="+componentName);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mEqFragment = new EqFragment();
        //mEqFragment.setValidHeight(getValidWidth());
        mEqFragment.setValidHeight(getValidHeight());
        mEqFragment.setOnItemChangedListener(new EqFragment.OnItemChangedListener() {
            @Override
            public void onProgressChanged(int index, int value) {
                if(mBinder != null) {
                    Log.i(TAG,"set eq"+index+" to "+value);
                    mBinder.adjustEq(index, value);
                }
            }

            @Override
            public void onSwitchChanged(boolean enable) {
                mBinder.enableEq(enable);
            }
        });

        mEqFragment.setEqEnabled(false);

        Intent i = new Intent(getApplicationContext(), EqualizerService.class);
        bindService(i, mConnection, Context.BIND_AUTO_CREATE);

        getFragmentManager().beginTransaction().
                replace(R.id.fragment_container, mEqFragment).commit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.e(TAG, "onDestroy");
        unbindService(mConnection);
    }

    private int getValidHeight() {
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        return dm.heightPixels;
    }

    private int getValidWidth() {
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        return dm.widthPixels;
    }

}
