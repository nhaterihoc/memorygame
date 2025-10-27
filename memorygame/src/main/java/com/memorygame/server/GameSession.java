package com.memorygame.server;

import java.util.Timer;
import java.util.TimerTask;

public class GameSession implements Runnable {
    private ClientHandler player1;
    private ClientHandler player2;
    private int totalRounds;
    private int currentRound = 0;
    private int p1Score = 0;
    private int p2Score = 0;
    private int displayTime;
    private int waitTime;

    private String currentPhrase;
    private String p1Answer = null;
    private String p2Answer = null;
    private long roundStartTime;

    private Server server;
    
    private Timer roundTimer;
    private boolean roundProcessed = false;
    private boolean gameEnded = false;

    public GameSession(ClientHandler player1, ClientHandler player2, int rounds, int displayTime, int waitTime, Server server) {
        this.player1 = player1;
        this.player2 = player2;
        this.totalRounds = rounds;
        this.displayTime = displayTime;
        this.waitTime = waitTime;
        this.server = server;
    }

    @Override
    public void run() {
        startNewRound();
    }

    private void startNewRound() {
        if (gameEnded) return;
        if (currentRound >= totalRounds) {
            endGame();
            return;
        }
        currentRound++;
        p1Answer = null;
        p2Answer = null;
        roundProcessed = false;

        currentPhrase = server.getRandomPhrase();
        String newRoundMessage = MessageProtocol.NEW_ROUND + MessageProtocol.SEPARATOR + currentRound + MessageProtocol.SEPARATOR + currentPhrase;
        player1.sendMessage(newRoundMessage);
        player2.sendMessage(newRoundMessage);

        if (roundTimer != null) {
            roundTimer.cancel();
        }
        roundTimer = new Timer();
        
        roundTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                player1.sendMessage(MessageProtocol.HIDE_PHRASE);
                player2.sendMessage(MessageProtocol.HIDE_PHRASE);
                roundStartTime = System.currentTimeMillis();

                roundTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        processRoundResults();
                    }
                }, waitTime);
            }
        }, displayTime);
    }

    public synchronized void setPlayerAnswer(ClientHandler player, String answer) {
        if (roundProcessed || gameEnded) return;

        if (player.getUsername().equals(player1.getUsername())) {
            p1Answer = answer;
        } else if (player.getUsername().equals(player2.getUsername())) {
            p2Answer = answer;
        }

        if (p1Answer != null && p2Answer != null) {
            processRoundResults();
        }
    }

    private synchronized void processRoundResults() {
        if (roundProcessed || gameEnded) return;
        roundProcessed = true;
        roundTimer.cancel();

        long timeElapsed = System.currentTimeMillis() - roundStartTime;
        long timeLeft = (waitTime - timeElapsed) / 1000;

        if (p1Answer != null && p1Answer.equals(currentPhrase)) {
            p1Score += calculatePoints(currentPhrase, timeLeft);
        }
        if (p2Answer != null && p2Answer.equals(currentPhrase)) {
            p2Score += calculatePoints(currentPhrase, timeLeft);
        }

        String scoreUpdate = MessageProtocol.UPDATE_SCORE + MessageProtocol.SEPARATOR + p1Score + MessageProtocol.SEPARATOR + p2Score;
        player1.sendMessage(scoreUpdate);
        player2.sendMessage(scoreUpdate);

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                startNewRound();
            }
        }, 2000); // Trễ 2 giây để sang round tiếp theo
    }

    private int calculatePoints(String phrase, long timeLeft) {
        if (timeLeft <= 0) return 0;
        return phrase.length() * (int) timeLeft;
    }

    private void endGame() {
        if (gameEnded) return;
        gameEnded = true;
        
        if (roundTimer != null) {
            roundTimer.cancel();
        }
        
        String p1Result, p2Result;
        if (p1Score > p2Score) {
            p1Result = "WIN"; p2Result = "LOSE";
        } else if (p2Score > p1Score) {
            p1Result = "LOSE"; p2Result = "WIN";
        } else {
            p1Result = "DRAW"; p2Result = "DRAW";
        }

        player1.sendMessage(MessageProtocol.GAME_RESULT + MessageProtocol.SEPARATOR + p1Result);
        player2.sendMessage(MessageProtocol.GAME_RESULT + MessageProtocol.SEPARATOR + p2Result);
        server.endGameSession(this, player1, player2);
    }

    public synchronized void handleDisconnect(ClientHandler disconnectedPlayer) {
        if (gameEnded) return;

        // Đánh dấu game kết thúc và dừng mọi timer
        gameEnded = true;
        if (roundTimer != null) {
            roundTimer.cancel();
        }

        // Xác định người thắng cuộc
        ClientHandler winner = (disconnectedPlayer.getUsername().equals(player1.getUsername())) 
                                ? player2 
                                : player1;
        
        System.out.println("Player " + disconnectedPlayer.getUsername() + " disconnected. " + winner.getUsername() + " wins by forfeit.");

        // Gửi tin nhắn chiến thắng (do bỏ cuộc) cho người chơi còn lại
        winner.sendMessage(MessageProtocol.GAME_RESULT + MessageProtocol.SEPARATOR + "WIN_FORFEIT");

        server.endGameSession(this, player1, player2);
    }
}