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

    private long p1AnswerTime = -1;
    private long p2AnswerTime = -1;
    
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
        p1AnswerTime = -1;
        p2AnswerTime = -1;
        roundProcessed = false;

        currentPhrase = server.getRandomPhrase();
        String newRoundMessage = MessageProtocol.NEW_ROUND + MessageProtocol.SEPARATOR + currentRound + MessageProtocol.SEPARATOR + currentPhrase;
        player1.sendMessage(newRoundMessage);
        if (player2 != null) {
            player2.sendMessage(newRoundMessage);
        }

        if (roundTimer != null) {
            roundTimer.cancel();
        }
        roundTimer = new Timer();
        
        roundTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                player1.sendMessage(MessageProtocol.HIDE_PHRASE);
                if (player2 != null) {
                    player2.sendMessage(MessageProtocol.HIDE_PHRASE);
                }
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

    private boolean allPlayersAnswered() {
        if (player2 == null) {
            // Chế độ luyện tập: chỉ cần p1 trả lời
            return p1Answer != null;
        } else {
            // Chế độ thách đấu: cần cả 2
            return p1Answer != null && p2Answer != null;
        }
    }

    public synchronized void setPlayerAnswer(ClientHandler player, String answer) {
        if (roundProcessed || gameEnded) return;

        long submissionTime = System.currentTimeMillis();

        if (player.getUsername().equals(player1.getUsername()) && p1Answer == null) {
            p1Answer = answer;
            p1AnswerTime = submissionTime;
        } else if (player2 != null && player.getUsername().equals(player2.getUsername()) && p2Answer == null) {
            p2Answer = answer;
            p2AnswerTime = submissionTime;
        }

        if (allPlayersAnswered()) {
            processRoundResults();
        }
    }

    private synchronized void processRoundResults() {
        if (roundProcessed || gameEnded) return;
        roundProcessed = true;
        roundTimer.cancel();

        if (p1Answer != null && p1Answer.equals(currentPhrase)) {
            if (player2 == null) { // Chế độ luyện tập
                p1Score += currentPhrase.length(); 
            } else { // Chế độ thách đấu
                long timeElapsed = p1AnswerTime - roundStartTime;
                long timeLeft = (waitTime - timeElapsed) / 1000;
                p1Score += calculatePoints(currentPhrase, timeLeft);
            }
        }

        if (player2 != null && p2Answer != null && p2Answer.equals(currentPhrase)) {
            long timeElapsed = p2AnswerTime - roundStartTime;
            long timeLeft = (waitTime - timeElapsed) / 1000;
            p2Score += calculatePoints(currentPhrase, timeLeft);
        }

        if (player2 != null) {
            String scoreUpdate = MessageProtocol.UPDATE_SCORE + MessageProtocol.SEPARATOR + p1Score + MessageProtocol.SEPARATOR + p2Score;
            player1.sendMessage(scoreUpdate);
            player2.sendMessage(scoreUpdate);
        } else {
            String scoreUpdate = MessageProtocol.UPDATE_SCORE + MessageProtocol.SEPARATOR + p1Score;
            player1.sendMessage(scoreUpdate);
        }

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
        
        if (player2 != null) {
            // Chế độ thách đấu: So sánh điểm và gửi kết quả
            String p1Result, p2Result;
            if (p1Score > p2Score) {
                p1Result = MessageProtocol.WIN; p2Result = MessageProtocol.LOSE;
            } else if (p1Score < p2Score) {
                p1Result = MessageProtocol.LOSE; p2Result = MessageProtocol.WIN;
            } else {
                p1Result = MessageProtocol.DRAW; p2Result = MessageProtocol.DRAW;
            }
            player1.sendMessage(MessageProtocol.GAME_RESULT + MessageProtocol.SEPARATOR + p1Result);
            player2.sendMessage(MessageProtocol.GAME_RESULT + MessageProtocol.SEPARATOR + p2Result);
        } else {
            // Chế độ luyện tập: Chỉ gửi thông báo hoàn thành, không lưu kết quả
            player1.sendMessage(MessageProtocol.GAME_RESULT + MessageProtocol.SEPARATOR + MessageProtocol.PRACTICE_COMPLETE);
        }

        server.endGameSession(this, player1, player2);
    }

    public synchronized void handleDisconnect(ClientHandler disconnectedPlayer) {
        if (gameEnded) return;

        gameEnded = true;
        if (roundTimer != null) {
            roundTimer.cancel();
        }

        if (player2 != null) {
            // Chế độ thách đấu
            ClientHandler winner = (disconnectedPlayer.getUsername().equals(player1.getUsername())) 
                                    ? player2 
                                    : player1;
            System.out.println("Player " + disconnectedPlayer.getUsername() + " disconnected. " + winner.getUsername() + " wins by forfeit.");
            winner.sendMessage(MessageProtocol.GAME_RESULT + MessageProtocol.SEPARATOR + MessageProtocol.WIN_FORFEIT);
        } else {
            // Chế độ luyện tập
            System.out.println("Player " + disconnectedPlayer.getUsername() + " disconnected from practice mode.");
        }

        server.endGameSession(this, player1, player2);
    }
}