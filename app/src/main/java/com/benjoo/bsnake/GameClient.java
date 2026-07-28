package com.benjoo.bsnake;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ConcurrentLinkedQueue;

class GameClient {

    private static final String TAG = "GameClient";
    private static final int BEACON_PORT = 5010;

    private final Context context;
    private Socket socket;
    private BufferedReader reader;
    private volatile OutputStreamWriter writer;
    private Thread readThread;
    private Thread discoveryThread;
    private volatile boolean running;
    private final ConcurrentLinkedQueue<String> incoming = new ConcurrentLinkedQueue<>();
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
            WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                multicastLock = wifi.createMulticastLock("BSnakeMulticast");
                multicastLock.acquire();
            }
            running = true;
            callback.onDiscoveryStarted();
            discoveryThread = new Thread(this::discoveryLoop);
            discoveryThread.start();
        } catch (Exception e) {
            Log.e(TAG, "Discovery failed", e);
            callback.onDiscoveryFailed();
        }
    }

    private void discoveryLoop() {
        try {
            MulticastSocket socket = new MulticastSocket(BEACON_PORT);
            InetAddress group = InetAddress.getByName("224.0.0.1");
            socket.joinGroup(group);
            socket.setSoTimeout(3000);
            byte[] buf = new byte[256];
            while (running) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(packet);
                    String data = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                    if (data.startsWith("BSNAKE:")) {
                        String[] parts = data.split(":");
                        if (parts.length >= 3) {
                            String name = parts[1];
                            String host = packet.getAddress().getHostAddress();
                            int port = Integer.parseInt(parts[2]);
                            callback.onHostFound(name, host, port);
                        }
                    }
                } catch (SocketTimeoutException e) {
                    // Timeout is normal, just keep listening
                }
            }
            try { socket.leaveGroup(group); } catch (Exception e) { }
            socket.close();
        } catch (Exception e) {
            Log.e(TAG, "Discovery loop failed", e);
        }
    }

    void connectTo(String host, int port) {
        new Thread(() -> connect(host, port)).start();
    }

    private void connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            socket.setTcpNoDelay(true);
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
        try { if (socket != null) socket.close(); } catch (Exception e) { }
    }
}
