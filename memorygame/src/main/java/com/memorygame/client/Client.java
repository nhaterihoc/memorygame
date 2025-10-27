package com.memorygame.client;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

/**Client để Test */
public class Client {
    private static final String SERVER_ADDRESS = "127.0.0.1"; // localhost
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             Scanner consoleScanner = new Scanner(System.in)) {

            System.out.println("Connected to the server.");

            // Tạo một luồng riêng chỉ để lắng nghe tin nhắn từ server
            Thread serverListener = new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = in.readLine()) != null) {
                        System.out.println("[SERVER SAYS]: " + serverMessage);
                    }
                } catch (IOException e) {
                    System.out.println("Disconnected from server.");
                }
            });
            serverListener.start();

            System.out.println("LOGIN|tuan|123"); // bừa
            System.out.println("LOGIN|hung|123");
            System.out.println("CHALLENGE_REQUEST|tuan|2|3000|10000"); // gửi từ client hung
            System.out.println("CHALLENGE_RESPONSE|ACCEPT|hung"); // gửi từ client tuan
            System.out.println("SUBMIT_ANSWER|test"); // câu trả lời đúng
            System.out.println("SUBMIT_ANSWER|fsfdsfdsfsd"); // câu trả lời sai
            System.out.println("------------------------------------");

            while (true) {
                String userInput = consoleScanner.nextLine();
                if ("exit".equalsIgnoreCase(userInput)) {
                    break;
                }
                out.println(userInput);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}