package com.benjoo.bsnake;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;

class GameServer {

    private static final String SERVICE_TYPE = "_bsnake._tcp";
    private static final String TAG = "GameServer";

    private final Context context;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private BufferedReader reader;
    private OutputStreamWriter writer;
    private NsdManager nsdManager;
    private NsdManager.RegistrationListener regListener;
    private Thread acceptThread;
    private Thread readThread;
    private volatile boolean running;
    private int port;
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

    boolean start() {
        try {
            serverSocket = new ServerSocket(0);
            port = serverSocket.getLocalPort();
            running = true;
            startAdvertising();
            acceptThread = new Thread(this::acceptLoop);
            acceptThread.start();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start server", e);
            return false;
        }
    }

    private void acceptLoop() {
        try {
            clientSocket = serverSocket.accept();
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
            writer.write(msg);
            writer.flush();
        } catch (Exception e) {
            Log.e(TAG, "Send failed", e);
        }
    }

    private void startAdvertising() {
        try {
            nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
            NsdServiceInfo serviceInfo = new NsdServiceInfo();
            serviceInfo.setServiceName("BSnake Game");
            serviceInfo.setServiceType(SERVICE_TYPE);
            serviceInfo.setPort(port);
            regListener = new NsdManager.RegistrationListener() {
                @Override public void onRegistrationFailed(NsdServiceInfo s, int e) { }
                @Override public void onUnregistrationFailed(NsdServiceInfo s, int e) { }
                @Override public void onServiceRegistered(NsdServiceInfo s) { }
                @Override public void onServiceUnregistered(NsdServiceInfo s) { }
            };
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, regListener);
        } catch (Exception e) {
            Log.e(TAG, "NSD register failed", e);
        }
    }

    void stop() {
        running = false;
        if (nsdManager != null && regListener != null) {
            try { nsdManager.unregisterService(regListener); } catch (Exception e) { }
        }
        try { if (clientSocket != null) clientSocket.close(); } catch (Exception e) { }
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception e) { }
    }
}
