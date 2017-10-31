package com.example.app.eq;

import android.content.Context;
import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;

import java.util.ArrayList;

public class DeviceFragment extends Fragment implements View.OnClickListener, AdapterView.OnItemClickListener{

    private String TAG = "DeviceFragment";

    private int mValidHeight = 0;
    private ArrayList<OnClickListener> mListener = null;
    private ArrayAdapter<String> mAdapter = null;
    private final int MAX_DEVICES = 15;
    private Context mContext = null;

    public DeviceFragment() {
        mListener = new ArrayList<>();
    }

    @Override
    public void onAttach (Context context) {
        super.onAttach(context);
        mContext = context;
        //Log.e(TAG,"onAttach?????");
        mAdapter = new ArrayAdapter<String>(mContext,
                android.R.layout.simple_list_item_1, new ArrayList<String>(MAX_DEVICES));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_device, container, false);
        Button b = v.findViewById(R.id.btn_scan);
        b.setOnClickListener(this);
        ListView listview = v.findViewById(R.id.ltv_devices);
        listview.setOnItemClickListener(this);
        listview.setAdapter(mAdapter);
        mAdapter.notifyDataSetChanged();
        return v;
    }

    public void setValidHeight(int height) {
        mValidHeight = height;
    }

    @Override
    public void onClick(View view) {
        if(mAdapter!= null) {
            mAdapter.clear();
            mAdapter.notifyDataSetChanged();
            for (OnClickListener listener : mListener) {
                listener.onScanClick();
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        for (OnClickListener listener : mListener) {
            listener.onItemClick(mAdapter.getItem(i));
        }
    }

    public void setOnClickListener(OnClickListener listener) {
        if(!mListener.contains(listener)) {
            mListener.add(listener);
        }
    }

    public void addItem(String s) {
        if(mAdapter != null) {
            if(mAdapter.getPosition(s) < 0) {
                mAdapter.add(s);
                mAdapter.notifyDataSetChanged();
            }
        }
    }

    public interface OnClickListener {
        void onItemClick(String name);
        void onScanClick();
    }

    public void notifyScanning(boolean scanning) {
        ProgressBar bar = getActivity().findViewById(R.id.pbr_scanning);
        if(scanning) {
            bar.setVisibility(View.VISIBLE);
        } else {
            bar.setVisibility(View.INVISIBLE);
        }
    }

}
