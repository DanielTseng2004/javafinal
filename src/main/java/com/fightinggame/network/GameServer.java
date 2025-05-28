// GameServer.java
package com.fightinggame.network;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class GameServer {
    private static final int MIN_PORT = 5000;
    private static final int MAX_PORT = 5100;
    private int port;
    private ServerSocket serverSocket;
    private boolean running;
    private List<ClientHandler> clients;
    private Thread acceptThread;

    public GameServer(int port) {
        this.port = port;
        this.clients = new CopyOnWriteArrayList<>();
    }

    public static int findAvailablePort() {
        for (int port = MIN_PORT; port <= MAX_PORT; port++) {
            try (ServerSocket socket = new ServerSocket(port)) {
                return port;
            } catch (IOException e) {
                continue;
            }
        }
        throw new RuntimeException("No available ports found between " + MIN_PORT + " and " + MAX_PORT);
    }

    public void start() {
        try {
            // Try using specified port, find available port if fails
            try {
                serverSocket = new ServerSocket(port, 0, null);
            } catch (IOException e) {
                System.out.println("Port " + port + " is in use, trying to find available port...");
                port = findAvailablePort();
                serverSocket = new ServerSocket(port, 0, null);
                System.out.println("Using port: " + port);
            }

            running = true;
            System.out.println("Server started on port: " + port);

            acceptThread = new Thread(() -> {
                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        ClientHandler clientHandler = new ClientHandler(clientSocket);
                        clients.add(clientHandler);
                        clientHandler.start();
                        System.out.println("New client connected. Total clients: " + clients.size());
                    } catch (IOException e) {
                        if (running) {
                            System.out.println("Error accepting client: " + e.getMessage());
                        }
                    }
                }
            });
            acceptThread.setDaemon(true);
            acceptThread.start();

        } catch (IOException e) {
            System.out.println("Error starting server: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
            for (ClientHandler client : clients) {
                client.stop();
            }
            clients.clear();
        } catch (IOException e) {
            System.out.println("Error stopping server: " + e.getMessage());
        }
    }

    public int getPort() {
        return port;
    }

    private class ClientHandler implements Runnable {
        private Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private boolean running;
        private Thread thread;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.running = true;
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());
            } catch (IOException e) {
                System.out.println("Error setting up client handler: " + e.getMessage());
                stop();
            }
        }

        public void start() {
            thread = new Thread(this);
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public void run() {
            try {
                while (running) {
                    GameMessage message = (GameMessage) in.readObject();
                    broadcastMessage(message, this);
                }
            } catch (EOFException e) {
                System.out.println("Client disconnected");
            } catch (Exception e) {
                System.out.println("Error handling client: " + e.getMessage());
            } finally {
                stop();
            }
        }

        public void sendMessage(GameMessage message) {
            if (!running) return;
            try {
                out.writeObject(message);
                out.flush();
            } catch (IOException e) {
                System.out.println("Error sending message to client: " + e.getMessage());
                stop();
            }
        }

        public void stop() {
            running = false;
            try {
                if (out != null) out.close();
                if (in != null) in.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                System.out.println("Error closing client connection: " + e.getMessage());
            }
            clients.remove(this);
            System.out.println("Client disconnected. Remaining clients: " + clients.size());
        }
    }

    private void broadcastMessage(GameMessage message, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) {
                client.sendMessage(message);
            }
        }
    }

    public static void main(String[] args) {
        GameServer server = new GameServer(5000);
        server.start();
    }
}