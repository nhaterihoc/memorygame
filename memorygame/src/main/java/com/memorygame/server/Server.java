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
    private Map<String, PendingChallenge> pendingChallenges = new ConcurrentHashMap<>();

    private static class PendingChallenge {
        String challenger;
        int rounds;
        int displayTime;
        int waitTime;

        public PendingChallenge(String challenger, int rounds, int displayTime, int waitTime) {
            this.challenger = challenger;
            this.rounds = rounds;
            this.displayTime = displayTime;
            this.waitTime = waitTime;
        }
    }

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
            String username = client.getUsername();
            
            // Kiểm tra xem người chơi này có trong một trận đấu không
            GameSession session = playerToSessionMap.get(username);
            
            if (session != null) {
                // Nếu có, báo cho GameSession biết để xử lý việc bỏ cuộc
                System.out.println("Player " + username + " was in a game. Handling disconnect...");
                session.handleDisconnect(client); 
            }

            // Xóa người chơi khỏi danh sách online
            onlineClients.remove(username);
            System.out.println("Client " + username + " has disconnected. Total: " + onlineClients.size());
        }
    }

    // Chế độ luyện tập
    public void handlePracticeRequest(ClientHandler player, int rounds, int displayTime, int waitTime) {
        System.out.println("Player " + player.getUsername() + " started a practice game.");
        
        // Tạo một GameSession mới với player2 là null
        GameSession session = new GameSession(player, null, rounds, displayTime, waitTime, this);
        
        playerToSessionMap.put(player.getUsername(), session);
        
        new Thread(session).start();
    }

    // Xử lý yêu cầu thách đấu
    public void handleChallengeRequest(String challenger, String opponent, String roundsStr, String displayTimeStr, String waitTimeStr) {
        ClientHandler opponentHandler = onlineClients.get(opponent);
        if (opponentHandler != null) {
            
            try {
                int rounds = Integer.parseInt(roundsStr);
                int displayTime = Integer.parseInt(displayTimeStr);
                int waitTime = Integer.parseInt(waitTimeStr);

                // Tạo và lưu lời mời đang chờ
                PendingChallenge challenge = new PendingChallenge(challenger, rounds, displayTime, waitTime);
                pendingChallenges.put(opponent, challenge);

                // Gửi lời mời đến đối thủ
                String invitation = MessageProtocol.INVITATION + MessageProtocol.SEPARATOR + challenger;
                opponentHandler.sendMessage(invitation);
                System.out.println("Sent invitation from " + challenger + " to " + opponent);

            } catch (NumberFormatException e) {
                System.err.println("Invalid challenge parameters received: " + e.getMessage());
            }

        } else {
            System.out.println("Challenge failed: Opponent " + opponent + " not found or not online.");
        }
    }
    
    // Xử lý phản hồi thách đấu
    public void handleChallengeResponse(String responder, String response, String challenger) {
        // Lấy và xóa lời mời đang chờ khỏi map
        PendingChallenge challenge = pendingChallenges.remove(responder);

        // Kiểm tra xem có lời mời hợp lệ không
        if (challenge == null || !challenge.challenger.equals(challenger)) {
            System.out.println("Invalid or expired challenge response from " + responder);
            return;
        }

        ClientHandler challengerHandler = onlineClients.get(challenger);
        ClientHandler responderHandler = onlineClients.get(responder);

        if (MessageProtocol.ACCEPT.equals(response) && challengerHandler != null && responderHandler != null) {
            System.out.println(responder + " accepted challenge from " + challenger + ". Starting game...");
            
            GameSession session = new GameSession(
                challengerHandler, 
                responderHandler, 
                challenge.rounds, 
                challenge.displayTime, 
                challenge.waitTime, 
                this
            );
            
            playerToSessionMap.put(challenger, session);
            playerToSessionMap.put(responder, session);
            
            new Thread(session).start();
        } else if (challengerHandler != null) {
            String rejectMessage = MessageProtocol.CHALLENGE_REJECTED + MessageProtocol.SEPARATOR + responder;
            challengerHandler.sendMessage(rejectMessage);
            System.out.println(responder + " rejected challenge from " + challenger);
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
        if (player1 != null) {
            playerToSessionMap.remove(player1.getUsername());
        }
        if (player2 != null) {
            playerToSessionMap.remove(player2.getUsername());
        }
        System.out.println("Game session ended.");
    }
    
    public String getRandomPhrase() { return "test"; } // chưa có database
    public boolean authenticateUser(String user, String pass) { return true; }

    public static void main(String[] args) {
        Server server = new Server();
        server.start();
    }
}
