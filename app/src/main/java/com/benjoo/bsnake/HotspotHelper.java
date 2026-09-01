package com.benjoo.bsnake;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

class HotspotHelper {

    private static final String TAG = "HotspotHelper";

    static boolean isWifiApEnabled(Context context) {
        try {
            WifiManager wifi = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifi == null) return false;
            Method method = wifi.getClass().getDeclaredMethod("isWifiApEnabled");
            method.setAccessible(true);
            return (boolean) method.invoke(wifi);
        } catch (Exception e) {
            return false;
        }
    }

    static boolean isHotspotEnabled(Context context) {
        try {
            WifiManager wifi = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifi == null) return false;

            if (Build.VERSION.SDK_INT >= 26) {
                android.net.wifi.WifiManager.LocalOnlyHotspotReservation reservation =
                        null;
                Method[] methods = wifi.getClass().getDeclaredMethods();
                for (Method m : methods) {
                    if (m.getName().equals("isWifiApEnabled")) {
                        m.setAccessible(true);
                        return (boolean) m.invoke(wifi);
                    }
                }
            } else {
                Method method = wifi.getClass().getDeclaredMethod("isWifiApEnabled");
                method.setAccessible(true);
                return (boolean) method.invoke(wifi);
            }
        } catch (Exception e) {
            // Fallback: try to detect via network interface
        }
        return isHotspotViaInterface();
    }

    private static boolean isHotspotViaInterface() {
        try {
            for (NetworkInterface intf : NetworkInterface.getNetworkInterfaces()) {
                String name = intf.getName();
                if (name.contains("wlan1") || name.contains("ap0")
                        || name.contains("softap")) {
                    return true;
                }
            }
        } catch (Exception e) { }
        return false;
    }

    static String getHotspotIpAddress(Context context) {
        try {
            WifiManager wifi = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                WifiInfo info = wifi.getConnectionInfo();
                if (info != null) {
                    int ip = info.getIpAddress();
                    if (ip != 0) {
                        return String.format("%d.%d.%d.%d",
                                ip & 0xff, (ip >> 8) & 0xff,
                                (ip >> 16) & 0xff, (ip >> 24) & 0xff);
                    }
                }
            }
        } catch (Exception e) { }
        return getIpAddressFromInterfaces();
    }

    private static String getIpAddressFromInterfaces() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                 en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIp = intf.getInetAddresses();
                     enumIp.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIp.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        String ip = inetAddress.getHostAddress();
                        if (ip != null && !ip.equals("0.0.0.0")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception e) { }
        return null;
    }

    static String getBroadcastAddress(Context context) {
        try {
            WifiManager wifi = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                WifiInfo info = wifi.getConnectionInfo();
                if (info != null) {
                    int ip = info.getIpAddress();
                    if (ip != 0) {
                        int broadcast = (ip & 0x00ffffff) | 0xff000000;
                        return String.format("%d.%d.%d.%d",
                                broadcast & 0xff, (broadcast >> 8) & 0xff,
                                (broadcast >> 16) & 0xff, (broadcast >> 24) & 0xff);
                    }
                }
            }
        } catch (Exception e) { }
        return "255.255.255.255";
    }

    static boolean isOnSameSubnet(String ip1, String ip2) {
        if (ip1 == null || ip2 == null) return false;
        String[] parts1 = ip1.split("\\.");
        String[] parts2 = ip2.split("\\.");
        if (parts1.length != 4 || parts2.length != 4) return false;
        // Compare first 3 octets (assuming /24 subnet)
        return parts1[0].equals(parts2[0])
                && parts1[1].equals(parts2[1])
                && parts1[2].equals(parts2[2]);
    }
}
