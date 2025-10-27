package com.memorygame.server;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private Server server;

    public ClientHandler(Socket socket, Server server) {
        this.clientSocket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            String inputLine;
            // Vòng lặp vô tận để lắng nghe tin nhắn từ client
            while ((inputLine = in.readLine()) != null) {
                System.out.println("Received from client " + username + ": " + inputLine);
                handleMessage(inputLine);
            }
        } catch (IOException e) {
            System.out.println("Client " + username + " disconnected.");
        } finally {
            // Xử lý khi client ngắt kết nối
            server.removeClient(this);
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleMessage(String message) {
        String[] parts = message.split("\\" + MessageProtocol.SEPARATOR);
        String command = parts[0];

        switch (command) {
            case MessageProtocol.LOGIN:
                // server.authenticateUser() luôn trả về true (để test)
                boolean isAuthenticated = server.authenticateUser(parts[1], parts[2]);
                if (isAuthenticated) {
                    this.username = parts[1];
                    server.addClient(this);
                    sendMessage(MessageProtocol.LOGIN_SUCCESS);
                } else {
                    sendMessage(MessageProtocol.LOGIN_FAIL);
                }
                break;

            case MessageProtocol.CHALLENGE_REQUEST:
                // parts[1]: đối thủ, parts[2]: số round, parts[3]: thời gian hiển thị từ, parts[4]: thời gian chờ
                server.handleChallengeRequest(this.username, parts[1], parts[2], parts[3], parts[4]);
                break;

            case MessageProtocol.CHALLENGE_RESPONSE:
                // parts[1]: "ACCEPT" hoặc "REJECT", parts[2]: người thách đấu
                server.handleChallengeResponse(this.username, parts[1], parts[2]);
                break;

            case MessageProtocol.SUBMIT_ANSWER:
                // parts[1]: câu trả lời
                server.handlePlayerAnswer(this, parts[1]);
                break;

            case MessageProtocol.PRACTICE_REQUEST:
                // parts[1]: số round, parts[2]: thời gian hiển thị từ, parts[3]: thời gian chờ
                try {
                    int rounds = Integer.parseInt(parts[1]);
                    int displayTime = Integer.parseInt(parts[2]);
                    int waitTime = Integer.parseInt(parts[3]);
                    server.handlePracticeRequest(this, rounds, displayTime, waitTime);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid practice parameters: " + message);
                }
                break;
        }
    }

    // Gửi tin nhắn đến client này
    public void sendMessage(String message) {
        out.println(message);
    }

    public String getUsername() {
        return username;
    }
}
