// GameClient.java
package com.fightinggame.network;

import java.io.EOFException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class GameClient {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String serverAddress;
    private int serverPort;
    private boolean connected;
    private BlockingQueue<GameMessage> messageQueue;
    private Thread receiveThread;

    public GameClient(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.messageQueue = new LinkedBlockingQueue<>();
        connect();
    }

    public void connect() {
        try {
            System.out.println("嘗試連接到服務器：" + serverAddress + ":" + serverPort);
            socket = new Socket(serverAddress, serverPort);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            connected = true;
            System.out.println("成功連接到服務器！");
    
            // Start message receiving thread
            receiveThread = new Thread(() -> {
                try {
                    while (connected) {
                        GameMessage message = (GameMessage) in.readObject();
                        messageQueue.offer(message);
                    }
                } catch (EOFException e) {
                    System.out.println("服務器關閉了連接");
                } catch (Exception e) {
                    System.out.println("接收消息時出錯: " + e.getMessage());
                } finally {
                    disconnect();
                }
            });
            receiveThread.setDaemon(true);
            receiveThread.start();
    
        } catch (Exception e) {
            System.out.println("連接錯誤: " + e.getMessage());
            connected = false;
        }
    }

    public void sendMessage(GameMessage message) {
        if (!connected) {
            return;
        }
        try {
            out.writeObject(message);
            out.flush();
        } catch (Exception e) {
            System.out.println("Error sending message: " + e.getMessage());
            disconnect();
        }
    }

    public GameMessage getNextMessage() {
        return messageQueue.poll();
    }

    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    public void disconnect() {
        connected = false;
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (Exception e) {
            System.out.println("Error during disconnect: " + e.getMessage());
        }
    }
}