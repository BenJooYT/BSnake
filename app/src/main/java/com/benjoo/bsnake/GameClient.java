package com.benjoo.bsnake;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;

class GameClient {

    private static final String SERVICE_TYPE = "_bsnake._tcp";
    private static final String TAG = "GameClient";

    private final Context context;
    private Socket socket;
    private BufferedReader reader;
    private OutputStreamWriter writer;
    private Thread readThread;
    private volatile boolean running;
    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;
    private final ConcurrentLinkedQueue<String> incoming = new ConcurrentLinkedQueue<>();
    private volatile String discoveredHost;
    private WifiManager.MulticastLock multicastLock;

    interface ClientCallback {
        void onDiscoveryStarted();
        void onDiscoveryFailed();
        void onHostFound(String name, String host, int port);
        void onConnected();
        void onConnectFailed();
        void onMessage(String msg);
        void onDisconnected();
    }

    private final ClientCallback callback;

    GameClient(Context context, ClientCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    void startDiscovery() {
        try {
            nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
            WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                multicastLock = wifi.createMulticastLock("BSnakeMulticast");
                multicastLock.acquire();
            }
            callback.onDiscoveryStarted();
            discoveryListener = new NsdManager.DiscoveryListener() {
                @Override public void onDiscoveryStarted(String t) { }
                @Override public void onDiscoveryStopped(String t) { }
                @Override
                public void onStartDiscoveryFailed(String t, int e) {
                    callback.onDiscoveryFailed();
                }
                @Override public void onStopDiscoveryFailed(String t, int e) { }
                @Override
                public void onServiceFound(NsdServiceInfo info) {
                    if (info.getServiceType().equals(SERVICE_TYPE)) {
                        discoveredHost = info.getServiceName();
                        nsdManager.resolveService(info, new NsdManager.ResolveListener() {
                            @Override public void onResolveFailed(NsdServiceInfo s, int e) {
                                Log.e(TAG, "NSD resolve failed error=" + e);
                            }
                            @Override
                            public void onServiceResolved(NsdServiceInfo res) {
                                callback.onHostFound(info.getServiceName(),
                                        res.getHost().getHostAddress(), res.getPort());
                            }
                        });
                    }
                }
                @Override public void onServiceLost(NsdServiceInfo info) { }
            };
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
        } catch (Exception e) {
            Log.e(TAG, "Discovery failed", e);
            callback.onDiscoveryFailed();
        }
    }

    void connectTo(String host, int port) {
        new Thread(() -> connect(host, port)).start();
    }

    private void connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new OutputStreamWriter(socket.getOutputStream());
            running = true;
            callback.onConnected();
            readThread = new Thread(this::readLoop);
            readThread.start();
        } catch (Exception e) {
            Log.e(TAG, "Connect failed", e);
            callback.onConnectFailed();
        }
    }

    private void readLoop() {
        try {
            String line;
            while (running && (line = reader.readLine()) != null) {
                incoming.add(line);
                callback.onMessage(line);
            }
        } catch (Exception e) { }
        callback.onDisconnected();
    }

    String pollMessage() {
        return incoming.poll();
    }

    void send(String msg) {
        if (writer == null) return;
        try {
            writer.write(msg);
            writer.flush();
        } catch (Exception e) {
            Log.e(TAG, "Send failed", e);
        }
    }

    void stop() {
        running = false;
        if (multicastLock != null) {
            try { multicastLock.release(); } catch (Exception e) { }
            multicastLock = null;
        }
        if (nsdManager != null && discoveryListener != null) {
            try { nsdManager.stopServiceDiscovery(discoveryListener); } catch (Exception e) { }
        }
        try { if (socket != null) socket.close(); } catch (Exception e) { }
    }
}
