package com.example.demo.bot;

import com.example.demo.ChatMessage;
import org.springframework.web.socket.WebSocketSession;

public abstract class Bot {
    protected String botName;
    protected String description;
    protected boolean isActive = true;

    public Bot(String botName, String description) {
        this.botName = botName;
        this.description = description;
    }

    // Abstract method mà mỗi bot phải implement
    public abstract ChatMessage processMessage(ChatMessage message, WebSocketSession session);

    // Phương thức check xem bot có xử lý message này không
    public abstract boolean canHandle(ChatMessage message);

    // Getters
    public String getBotName() { return botName; }
    public String getDescription() { return description; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    // Helper method để tạo bot response
    protected ChatMessage createBotResponse(String message, String room) {
        return new ChatMessage("🤖 " + botName, message, room);
    }
}
