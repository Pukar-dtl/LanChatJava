package com.lanchat.net;

import com.lanchat.db.DatabaseManager;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {

    public interface ServerListener {
        void onLog(String line);
        void onClientCount(int count);
    }

    private final int port;
    private final DatabaseManager db;
    private final ServerListener listener;
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private volatile boolean running;

    public ChatServer(int port, DatabaseManager db, ServerListener listener) {
        this.port = port;
        this.db = db;
        this.listener = listener;
    }

    public void start() throws IOException {
        running = true;
        ServerSocket ss = new ServerSocket(port);
        log("Server listening on port " + port);
        Executors.newSingleThreadExecutor().submit(() -> {
            while (running) {
                try {
                    Socket s = ss.accept();
                    new ClientHandler(s, this).start();
                } catch (IOException e) {
                    if (running) log("Accept error: " + e.getMessage());
                }
            }
        });
    }

    public void stop() { running = false; }

    void log(String s) { if (listener != null) listener.onLog(s); }

    synchronized boolean register(String name, ClientHandler h) {
        if (clients.containsKey(name)) return false;
        clients.put(name, h);
        listener.onClientCount(clients.size());
        broadcast("JOIN|" + name, name);
        broadcastRoster();
        return true;
    }

    synchronized void unregister(String name) {
        if (clients.remove(name) != null) {
            broadcast("LEAVE|" + name, name);
            broadcastRoster();
            listener.onClientCount(clients.size());
        }
    }

    void broadcastRoster() {
        String roster = "USERS|" + String.join(",", clients.keySet());
        for (ClientHandler c : clients.values()) c.send(roster);
    }

    void broadcast(String line, String except) {
        for (Map.Entry<String, ClientHandler> e : clients.entrySet()) {
            if (!e.getKey().equals(except)) e.getValue().send(line);
        }
    }

    void routeMessage(String sender, String recipient, String text) {
        try {
            long id = db.saveMessage(sender, recipient, text);
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String out = "MSG|" + sender + "|" + recipient + "|" + id + "|" + ts + "|" + text;
            if ("ALL".equals(recipient)) {
                for (ClientHandler c : clients.values()) c.send(out);
            } else {
                ClientHandler target = clients.get(recipient);
                ClientHandler self = clients.get(sender);
                if (target != null) target.send(out);
                if (self != null && self != target) self.send(out);
                if (target == null && self != null) self.send("SYS|User " + recipient + " is not online.");
            }
        } catch (Exception ex) {
            log("DB error: " + ex.getMessage());
        }
    }
}