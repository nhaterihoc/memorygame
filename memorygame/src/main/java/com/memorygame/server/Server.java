package com.memorygame.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class Server {
    private static final int PORT = 12345;
    private Map<String, ClientHandler> onlineClients = new ConcurrentHashMap<>();
    private Map<String, GameSession> playerToSessionMap = new ConcurrentHashMap<>();

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is listening on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getRemoteSocketAddress());
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.err.println("Server exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Phương thức để ClientHandler đăng ký khi login thành công
    public synchronized void addClient(ClientHandler client) {
        onlineClients.put(client.getUsername(), client);
        System.out.println("Client " + client.getUsername() + " is now online. Total: " + onlineClients.size());
    }
    
    // Xử lý khi client ngắt kết nối
    public synchronized void removeClient(ClientHandler client) {
        if (client.getUsername() != null) {
            onlineClients.remove(client.getUsername());
            System.out.println("Client " + client.getUsername() + " has disconnected. Total: " + onlineClients.size());
        }
    }

    // Xử lý yêu cầu thách đấu
    public void handleChallengeRequest(String challenger, String opponent, String rounds, String displayTime, String waitTime) {
        ClientHandler opponentHandler = onlineClients.get(opponent);
        if (opponentHandler != null) {
            // Gửi lời mời đến đối thủ
            String invitation = MessageProtocol.INVITATION + MessageProtocol.SEPARATOR + challenger;
            opponentHandler.sendMessage(invitation);
            System.out.println("Sent invitation from " + challenger + " to " + opponent);
        } else {
            System.out.println("Challenge failed: Opponent " + opponent + " not found or not online.");
        }
    }
    
    // Xử lý phản hồi thách đấu
    public void handleChallengeResponse(String responder, String response, String challenger) {
        ClientHandler challengerHandler = onlineClients.get(challenger);
        ClientHandler responderHandler = onlineClients.get(responder);

        if ("ACCEPT".equals(response) && challengerHandler != null && responderHandler != null) {
            System.out.println(responder + " accepted challenge from " + challenger + ". Starting game...");
            
            // Thời gian hiển thị từ đang để là 3 giây, thời gian chờ đáp án đang để là 30 giây
            GameSession session = new GameSession(challengerHandler, responderHandler, 5, 3000, 30000, this);
            
            // LƯU LẠI AI ĐANG Ở TRẬN NÀO
            playerToSessionMap.put(challenger, session);
            playerToSessionMap.put(responder, session);
            
            new Thread(session).start();
        } else if (challengerHandler != null) {
            challengerHandler.sendMessage("Challenge rejected by " + responder);
        }
    }

    public void handlePlayerAnswer(ClientHandler player, String answer) {
        GameSession session = playerToSessionMap.get(player.getUsername());
        if (session != null) {
            session.setPlayerAnswer(player, answer);
        } else {
            System.out.println("ERROR: Received answer from player " + player.getUsername() + " who is not in a game session.");
        }
    }

    public void endGameSession(GameSession session, ClientHandler player1, ClientHandler player2) {
        // Dọn dẹp map sau khi game kết thúc
        playerToSessionMap.remove(player1.getUsername());
        playerToSessionMap.remove(player2.getUsername());
        System.out.println("Game session ended for " + player1.getUsername() + " and " + player2.getUsername());
    }
    
    // Các phương thức dùng để Test
    public String getRandomPhrase() { return "test"; } // chưa có database
    public boolean authenticateUser(String user, String pass) { return true; } // Luôn cho login thành công để test

    public static void main(String[] args) {
        Server server = new Server();
        server.start();
    }
}
