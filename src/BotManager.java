package com.example.demo.bot;

import com.example.demo.ChatMessage;
import com.example.demo.moderation.ModerationService;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BotManager {
    
    private Map<String, Bot> bots = new ConcurrentHashMap<>();
    private ModerationService moderationService = new ModerationService();
    private ModerationBot moderationBot;
    
    public BotManager() {
        initializeBots();
    }
    
    private void initializeBots() {
        // Khởi tạo các bot cơ bản
        bots.put("helpbot", new HelpBot());
        bots.put("quizbot", new QuizBot());
        bots.put("spamdetector", new SpamDetectionBot());
        
        // Moderation bot cần ModerationService
        moderationBot = new ModerationBot(moderationService);
        bots.put("moderationbot", moderationBot);
        
        // Bot creator bot
        bots.put("botcreator", new BotCreatorBot(this));
    }
    
    // Xử lý tin nhắn qua tất cả bot
    public List<ChatMessage> processMessage(ChatMessage message, WebSocketSession session) {
        List<ChatMessage> responses = new ArrayList<>();
        
        // Kiểm tra user có bị mute/ban không
        String username = message.getNickname();
        if (!username.startsWith("🤖")) { // Không check bot messages
            if (!moderationBot.canUserSendMessage(username)) {
                String status = moderationBot.getUserModerationStatus(username);
                if ("BANNED".equals(status)) {
                    responses.add(new ChatMessage("🤖 System", 
                        String.format("❌ @%s đã bị cấm chat vĩnh viễn và không thể gửi tin nhắn.", username), 
                        message.getRoom()));
                    return responses;
                } else if ("MUTED".equals(status)) {
                    String timeLeft = moderationService.getRemainingMuteTime(username);
                    responses.add(new ChatMessage("🤖 System", 
                        String.format("🔇 @%s đang bị tắt tiếng. Thời gian còn lại: %s", username, timeLeft), 
                        message.getRoom()));
                    return responses;
                }
            }
        }
        
        // Xử lý tin nhắn qua các bot
        for (Bot bot : bots.values()) {
            if (bot.isActive() && bot.canHandle(message)) {
                try {
                    ChatMessage response = bot.processMessage(message, session);
                    if (response != null) {
                        responses.add(response);
                    }
                } catch (Exception e) {
                    System.err.println("Error processing message with bot " + bot.getBotName() + ": " + e.getMessage());
                }
            }
        }
        
        return responses;
    }
    
    // Tạo bot mới (cho admin/teacher)
    public boolean createCustomBot(String botName, String description, String creator) {
        if (bots.containsKey(botName.toLowerCase())) {
            return false; // Bot đã tồn tại
        }
        
        // Tạo custom bot đơn giản
        Bot customBot = new CustomBot(botName, description, creator);
        bots.put(botName.toLowerCase(), customBot);
        return true;
    }
    
    // Xóa bot (chỉ custom bot)
    public boolean removeBot(String botName) {
        String key = botName.toLowerCase();
        Bot bot = bots.get(key);
        if (bot instanceof CustomBot) {
            bots.remove(key);
            return true;
        }
        return false; // Không thể xóa bot hệ thống
    }
    
    // Bật/tắt bot
    public boolean toggleBot(String botName) {
        Bot bot = bots.get(botName.toLowerCase());
        if (bot != null) {
            bot.setActive(!bot.isActive());
            return true;
        }
        return false;
    }
    
    // Lấy danh sách bot
    public Map<String, Bot> getAllBots() {
        return new HashMap<>(bots);
    }
    
    // Lấy bot cụ thể
    public Bot getBot(String botName) {
        return bots.get(botName.toLowerCase());
    }
    
    // Lấy ModerationService để sử dụng bên ngoài
    public ModerationService getModerationService() {
        return moderationService;
    }
    
    // Custom Bot class cho bot do user tạo
    public static class CustomBot extends Bot {
        private String creator;
        private Map<String, String> responses = new HashMap<>();
        
        public CustomBot(String botName, String description, String creator) {
            super(botName, description);
            this.creator = creator;
            
            // Thêm một số response mặc định
            responses.put("hello", "Xin chào! Tôi là " + botName + " được tạo bởi @" + creator);
            responses.put("help", "Tôi là bot tùy chỉnh. Gõ 'hello' để chào hỏi!");
            responses.put("info", "Bot: " + botName + "\nMô tả: " + description + "\nTạo bởi: @" + creator);
        }
        
        @Override
        public boolean canHandle(ChatMessage message) {
            String msg = message.getMessage().toLowerCase();
            return msg.contains(botName.toLowerCase()) || 
                   msg.startsWith("/" + botName.toLowerCase()) ||
                   responses.keySet().stream().anyMatch(msg::contains);
        }
        
        @Override
        public ChatMessage processMessage(ChatMessage message, WebSocketSession session) {
            String msg = message.getMessage().toLowerCase();
            
            // Tìm response phù hợp
            for (Map.Entry<String, String> entry : responses.entrySet()) {
                if (msg.contains(entry.getKey())) {
                    return createBotResponse(entry.getValue(), message.getRoom());
                }
            }
            
            // Response mặc định
            return createBotResponse(
                "Xin chào! Tôi là " + botName + ". Gõ 'help' để biết thêm thông tin!", 
                message.getRoom()
            );
        }
        
        // Thêm response mới
        public void addResponse(String trigger, String response) {
            responses.put(trigger.toLowerCase(), response);
        }
        
        // Getters
        public String getCreator() { return creator; }
        public Map<String, String> getResponses() { return new HashMap<>(responses); }
    }
}
