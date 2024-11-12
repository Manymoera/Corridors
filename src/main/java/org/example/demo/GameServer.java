package org.example.demo;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class GameServer {
    private static final int PORT = 12345;
    private static List<ObjectOutputStream> clientStreams = new ArrayList<>();
    private static List<String> playerIds = new ArrayList<>();
    private static ServerSocket serverSocket;
    private Map<String, ObjectOutputStream> players = new HashMap<>();

    public static void main(String[] args) {
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Server started on port " + PORT);
            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                clientStreams.add(out);

                // Отправляем сообщение о команде новому клиенту
                String teamMessage = clientStreams.size() == 1 ? "Your team: RED" : "Your team: BLUE";
                out.writeObject("teamMessage:" + teamMessage);
                out.flush();

                // Запускаем поток для обработки клиента
                new Thread(new ClientHandler(clientSocket, out)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private ObjectOutputStream out;

        public ClientHandler(Socket socket, ObjectOutputStream out) {
            this.clientSocket = socket;
            this.out = out;
        }

        @Override
        public void run() {
            try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())) {
                while (!serverSocket.isClosed()) {
                    String tag = (String) in.readObject();
                    System.out.println("Received: " + tag);
                    switch (tag) {
                        case "ID" -> {
                            String message = (String) in.readObject();
                            playerIds.add(message);
                            System.out.println("ID: " + playerIds);
                        }
                        case "MOVE" -> {
                            String moveID = (String) in.readObject();
                            System.out.println("Received: " + moveID);
                            String message = (String) in.readObject();
                            System.out.println("Received: " + message);
                            sendToAllClients(message, moveID);
                        }
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        private void sendToAllClients(String message, String moveID) {
            for (ObjectOutputStream clientOut : clientStreams) {
                try {
                    clientOut.writeObject(message);
                    clientOut.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

