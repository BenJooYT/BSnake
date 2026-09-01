package com.benjoo.bsnake;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;

class GameServer {

    private static final String TAG = "GameServer";
    private static final int BEACON_PORT = 5010;
    private static final int BROADCAST_PORT = 5011;

    private final Context context;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private BufferedReader reader;
    private volatile OutputStreamWriter writer;
    private Thread acceptThread;
    private Thread readThread;
    private Thread beaconThread;
    private volatile boolean running;
    private int port;
    private WifiManager.MulticastLock multicastLock;
    private final ConcurrentLinkedQueue<String> incoming = new ConcurrentLinkedQueue<>();

    interface ServerCallback {
        void onClientConnected();
        void onMessage(String msg);
        void onClientDisconnected();
    }

    private final ServerCallback callback;

    GameServer(Context context, ServerCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    int getPort() {
        return port;
    }

    boolean start() {
        try {
            serverSocket = new ServerSocket(0);
            port = serverSocket.getLocalPort();
            WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                multicastLock = wifi.createMulticastLock("BSnakeMulticastServer");
                multicastLock.acquire();
            }
            running = true;
            startBeacon();
            acceptThread = new Thread(this::acceptLoop);
            acceptThread.start();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start server", e);
            return false;
        }
    }

    private void startBeacon() {
        beaconThread = new Thread(() -> {
            String device = Build.MODEL != null ? Build.MODEL : "Android";
            try {
                boolean isHotspot = HotspotHelper.isWifiApEnabled(context)
                        || HotspotHelper.isHotspotEnabled(context);
                MulticastSocket beaconSocket = new MulticastSocket();
                beaconSocket.setBroadcast(true);

                while (running) {
                    String data = "BSNAKE:" + device + ":" + port;
                    byte[] buf = data.getBytes("UTF-8");

                    if (isHotspot) {
                        String broadcastAddr = HotspotHelper.getBroadcastAddress(context);
                        InetAddress broadcast = InetAddress.getByName(broadcastAddr);
                        DatagramPacket packet = new DatagramPacket(buf, buf.length, broadcast, BROADCAST_PORT);
                        beaconSocket.send(packet);
                        // Also try 255.255.255.255 as fallback
                        try {
                            InetAddress fallback = InetAddress.getByName("255.255.255.255");
                            DatagramPacket fallbackPacket = new DatagramPacket(buf, buf.length, fallback, BROADCAST_PORT);
                            beaconSocket.send(fallbackPacket);
                        } catch (Exception e) { }
                    } else {
                        beaconSocket.setTimeToLive(1);
                        InetAddress group = InetAddress.getByName("224.0.0.1");
                        DatagramPacket packet = new DatagramPacket(buf, buf.length, group, BEACON_PORT);
                        beaconSocket.send(packet);
                    }
                    Thread.sleep(2000);
                }
                beaconSocket.close();
            } catch (Exception e) {
                Log.e(TAG, "Beacon failed", e);
            }
        });
        beaconThread.start();
    }

    private void acceptLoop() {
        try {
            clientSocket = serverSocket.accept();
            clientSocket.setTcpNoDelay(true);
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            writer = new OutputStreamWriter(clientSocket.getOutputStream());
            callback.onClientConnected();
            readThread = new Thread(this::readLoop);
            readThread.start();
        } catch (Exception e) {
            Log.e(TAG, "Accept failed", e);
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
        callback.onClientDisconnected();
    }

    String pollMessage() {
        return incoming.poll();
    }

    void send(String msg) {
        if (writer == null) return;
        try {
            synchronized (writer) {
                writer.write(msg);
                writer.flush();
            }
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
        try { if (clientSocket != null) clientSocket.close(); } catch (Exception e) { }
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception e) { }
    }
}
