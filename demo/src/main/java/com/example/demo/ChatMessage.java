package com.example.demo;

public class ChatMessage {
    private String nickname;
    private String message;
    private String room;

    // Constructor mặc định
    public ChatMessage() {
    }

    // Constructor đầy đủ
    public ChatMessage(String nickname, String message, String room) {
        this.nickname = nickname;
        this.message = message;
        this.room = room;
    }

    // Getter và Setter cho nickname
    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    // Getter và Setter cho message
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    // Getter và Setter cho room
    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    // Phương thức toString để debug
    @Override
    public String toString() {
        return "ChatMessage{" +
                "nickname='" + nickname + '\'' +
                ", message='" + message + '\'' +
                ", room='" + room + '\'' +
                '}';
    }
}
