package com.lanchat.net;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler extends Thread {

    private final Socket socket;
    private final ChatServer server;
    private BufferedReader in;
    private PrintWriter out;
    private String username;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            String line;
            while ((line = in.readLine()) != null) {
                handle(line);
            }
        } catch (IOException ignored) {
        } finally {
            if (username != null) server.unregister(username);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void handle(String line) {
        String[] parts = line.split("\\|", -1);
        switch (parts[0]) {
            case "LOGIN" -> {
                String name = parts[1].trim();
                if (name.isEmpty() || name.length() > 64) {
                    send("ERROR|Invalid name");
                    return;
                }
                if (server.register(name, this)) {
                    username = name;
                    send("OK|Welcome " + name);
                } else {
                    send("ERROR|Name already taken");
                }
            }
            case "MSG" -> {
                if (username == null) return;
                String recipient = parts[1];
                String text = parts.length > 2 ? parts[2] : "";
                if (!text.isBlank()) server.routeMessage(username, recipient, text);
            }
            case "LOGOUT" -> {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    public synchronized void send(String line) {
        if (out != null) out.println(line);
    }
}