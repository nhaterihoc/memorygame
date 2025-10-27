package com.memorygame.server;

public class MessageProtocol {
    public static final String SEPARATOR = "|";
    // Client to Server
    public static final String LOGIN = "LOGIN";
    public static final String PRACTICE_REQUEST = "PRACTICE_REQUEST";
    public static final String CHALLENGE_REQUEST = "CHALLENGE_REQUEST";
    public static final String CHALLENGE_RESPONSE = "CHALLENGE_RESPONSE";
    public static final String SUBMIT_ANSWER = "SUBMIT_ANSWER";
    public static final String LOGOUT = "LOGOUT";
    public static final String OUT_GAME = "OUT_GAME";

    // Server to Client
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAIL = "LOGIN_FAIL";
    public static final String ONLINE_LIST = "ONLINE_LIST";
    public static final String INVITATION = "INVITATION"; // Gửi lời mời thách đấu
    public static final String GAME_START = "GAME_START";
    public static final String NEW_ROUND = "NEW_ROUND"; // Gửi từ vựng mới
    public static final String HIDE_PHRASE = "HIDE_PHRASE"; // Yêu cầu ẩn từ
    public static final String UPDATE_SCORE = "UPDATE_SCORE";
    public static final String GAME_RESULT = "GAME_RESULT"; // WIN, LOSE, DRAW
}
