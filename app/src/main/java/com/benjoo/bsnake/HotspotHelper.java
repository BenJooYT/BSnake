package com.benjoo.bsnake;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

class HotspotHelper {

    private static final String TAG = "HotspotHelper";

    private static final String[] HOTSPOT_IFACES = {"wlan1", "ap0", "softap"};

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
        if (isWifiApEnabled(context)) return true;
        return isHotspotViaInterface();
    }

    private static boolean isHotspotViaInterface() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface intf = interfaces.nextElement();
                String name = intf.getName();
                for (String hs : HOTSPOT_IFACES) {
                    if (name.contains(hs)) return true;
                }
            }
        } catch (Exception e) { }
        return false;
    }

    static String getHotspotIpAddress(Context context) {
        // Try WifiManager first (works on some devices)
        try {
            WifiManager wifi = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                WifiInfo info = wifi.getConnectionInfo();
                if (info != null) {
                    int ip = info.getIpAddress();
                    if (ip != 0) {
                        return formatIp(ip);
                    }
                }
            }
        } catch (Exception e) { }

        // Walk interfaces looking for a hotspot interface, then any non-loopback IPv4
        try {
            String hotspotIp = null;
            String fallbackIp = null;
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface intf = interfaces.nextElement();
                if (!intf.isUp() || intf.isLoopback()) continue;
                String ip = getIpv4FromInterface(intf);
                if (ip == null) continue;
                for (String hs : HOTSPOT_IFACES) {
                    if (intf.getName().contains(hs)) {
                        hotspotIp = ip;
                        break;
                    }
                }
                if (hotspotIp == null && fallbackIp == null) {
                    fallbackIp = ip;
                }
            }
            if (hotspotIp != null) return hotspotIp;
            if (fallbackIp != null) return fallbackIp;
        } catch (Exception e) { }
        return null;
    }

    static String getBroadcastAddress(Context context) {
        String ip = getHotspotIpAddress(context);
        if (ip != null) {
            try {
                String[] parts = ip.split("\\.");
                if (parts.length == 4) {
                    // Assume /24 subnet — set last octet to 255
                    return parts[0] + "." + parts[1] + "." + parts[2] + ".255";
                }
            } catch (Exception e) { }
        }
        return "255.255.255.255";
    }

    private static String getIpv4FromInterface(NetworkInterface intf) {
        try {
            for (Enumeration<InetAddress> enumIp = intf.getInetAddresses();
                 enumIp.hasMoreElements(); ) {
                InetAddress addr = enumIp.nextElement();
                if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                    String ip = addr.getHostAddress();
                    if (ip != null && !ip.equals("0.0.0.0")) {
                        return ip;
                    }
                }
            }
        } catch (Exception e) { }
        return null;
    }

    private static String formatIp(int ip) {
        return String.format("%d.%d.%d.%d",
                ip & 0xff, (ip >> 8) & 0xff, (ip >> 16) & 0xff, (ip >> 24) & 0xff);
    }
}
