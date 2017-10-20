package com.jshipster.socket;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.util.Log;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class NetworkChecker {

    private static final String TAG = "NetworkChecker";
    private Context mContext = null;
    private ConnectivityManager mCm = null;

    public NetworkChecker(Context context) {
        mContext = context;
        mCm = (ConnectivityManager) mContext.getSystemService(mContext.CONNECTIVITY_SERVICE);
    }

    public NetworkConfig[] getIpInfo() {
        ArrayList<NetworkConfig> ips = new ArrayList<>();

        if(!isConnected()) {
            Log.e(TAG, "wifi is not connected");
            return null;
        }

        Network[] networks = mCm.getAllNetworks();
        for(Network net : networks) {
            LinkProperties props = mCm.getLinkProperties(net);
            List<LinkAddress> addrs = props.getLinkAddresses();
            for(LinkAddress l : addrs) {
                if(l.getAddress().isLinkLocalAddress() || l.getAddress().isLoopbackAddress()) {
                    continue;
                }
                ips.add(new NetworkConfig(l));
            }
        }
        if(ips.isEmpty()) {
            return null;
        }
        NetworkConfig[] nets = new NetworkConfig[ips.size()];
        return ips.toArray(nets);
    }

    public boolean isLocalIp(String ip) {
        NetworkConfig[] ips = getIpInfo();
        if(ips != null) {
            for(NetworkConfig s : ips) {
                if(s.getIp().equals(ip)) {
                    return true;
                }
            }
        }
        return false;
    }


    public boolean isConnected() {
        if (mCm == null) {
            Log.e(TAG, "get ConnectivityManager failed");
            return false;
        }
        NetworkInfo info = mCm.getActiveNetworkInfo();
        if(info == null ) {
            return false;
        }
        return info.isConnected();
    }

    public static class NetworkConfig {
        private LinkAddress mLinkAddress = null;

        //Do not create this class outside
        private NetworkConfig() {

        }

        private NetworkConfig(LinkAddress address) {
            mLinkAddress = address;
        }

        private String convertIntToIpString(int ip) {
            StringBuilder b = new StringBuilder();
            b.append(ip>>24 & 0xff).append(".").
                    append(ip>>16 & 0xff).append(".").
                    append(ip>>8 & 0xff).append(".").
                    append(ip & 0xff);
            return b.toString();
        }

        private int convertIpStringToInt(String ip) throws UnknownHostException {
            int raw = 0;

            InetAddress addr = InetAddress.getByName(ip);
            for (byte b : addr.getAddress()) {
                raw = (raw << 8) | (b &0xff);
            }

            return raw;
        }

        public String getIp() {
            return mLinkAddress.getAddress().getHostAddress();
        }

        public String getBroadcastIp() {
            int mask = getMask();
            String ip = null;
            try {
                ip = convertIntToIpString(convertIpStringToInt(mLinkAddress.getAddress().getHostAddress()) | ~mask);
            } catch (UnknownHostException e) {
                e.printStackTrace();
                return null;
            }
            return ip;
        }

        public String getSubmask() {
            int mask = getMask();
            return convertIntToIpString(mask);
        }

        public boolean isTheSameSubnet(String ip) {
            int mask = getMask();
            boolean ret = false;
            try {
                int local = convertIpStringToInt(mLinkAddress.getAddress().getHostAddress());
                int remote = convertIpStringToInt(ip);
                if((local & mask) == (remote & mask)) {
                        ret = true;
                }
            } catch (UnknownHostException e) {
                e.printStackTrace();
                return false;
            }
            return ret;
        }

        private int getMask() {
            int prefix = mLinkAddress.getPrefixLength();
            int raw = 0;
            int mask = 0x80000000;
            for(int i=0; i<prefix; i++) {
                raw |= mask;
                mask >>= 1;
            }
            return raw;
        }
    }

}
