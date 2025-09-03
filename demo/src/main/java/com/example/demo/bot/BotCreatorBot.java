package com.example.demo.bot;

import com.example.demo.ChatMessage;
import org.springframework.web.socket.WebSocketSession;

public class BotCreatorBot extends Bot {
    
    private BotManager botManager;
    
    public BotCreatorBot(BotManager botManager) {
        super("BotCreator", "Bot hỗ trợ tạo bot mới");
        this.botManager = botManager;
    }

    @Override
    public boolean canHandle(ChatMessage message) {
        String msg = message.getMessage().toLowerCase();
        return msg.startsWith("/create-bot") || msg.startsWith("/delete-bot") || 
               msg.startsWith("/list-bots") || msg.startsWith("/toggle-bot");
    }

    @Override
    public ChatMessage processMessage(ChatMessage message, WebSocketSession session) {
        String command = message.getMessage().trim();
        String userRole = getUserRole(message.getNickname());
        
        // Chỉ admin và teacher mới có thể quản lý bot
        if (!hasPermission(userRole)) {
            return createBotResponse(
                "❌ Bạn không có quyền quản lý bot. Chỉ **Admin** và **Giáo viên** mới có thể tạo/quản lý bot.", 
                message.getRoom()
            );
        }
        
        if (command.startsWith("/create-bot")) {
            return handleCreateBot(command, message.getNickname(), message.getRoom());
        } else if (command.startsWith("/delete-bot")) {
            return handleDeleteBot(command, message.getRoom());
        } else if (command.startsWith("/list-bots")) {
            return handleListBots(message.getRoom());
        } else if (command.startsWith("/toggle-bot")) {
            return handleToggleBot(command, message.getRoom());
        }
        
        return null;
    }
    
    private ChatMessage handleCreateBot(String command, String creator, String room) {
        String[] parts = command.split("\\s+", 3);
        if (parts.length < 3) {
            return createBotResponse(
                "❌ Sử dụng: `/create-bot <tên_bot> <mô_tả>`\nVí dụ: `/create-bot StudyHelper Bot hỗ trợ học tập`", 
                room
            );
        }
        
        String botName = parts[1];
        String description = parts[2];
        
        // Kiểm tra tên bot hợp lệ
        if (!botName.matches("^[a-zA-Z0-9_]+$")) {
            return createBotResponse(
                "❌ Tên bot chỉ được chứa chữ cái, số và dấu gạch dưới!", 
                room
            );
        }
        
        boolean success = botManager.createCustomBot(botName, description, creator);
        
        if (success) {
            return createBotResponse(String.format("""
                ✅ **BOT ĐÃ ĐƯỢC TẠO THÀNH CÔNG!**
                
                🤖 Tên bot: %s
                📝 Mô tả: %s
                👨‍💼 Người tạo: @%s
                
                💡 **Cách sử dụng:**
                • Gọi bot: `@%s` hoặc `/%s`
                • Bot sẽ tự động phản hồi khi được nhắc đến
                • Gõ `/%s help` để xem hướng dẫn
                
                🔧 **Quản lý bot:**
                • `/toggle-bot %s` - Bật/tắt bot
                • `/delete-bot %s` - Xóa bot
                """, botName, description, creator, botName, botName.toLowerCase(), botName.toLowerCase(), botName, botName), room);
        } else {
            return createBotResponse(
                String.format("❌ Không thể tạo bot '%s'. Bot có thể đã tồn tại!", botName), 
                room
            );
        }
    }
    
    private ChatMessage handleDeleteBot(String command, String room) {
        String[] parts = command.split("\\s+");
        if (parts.length < 2) {
            return createBotResponse("❌ Sử dụng: `/delete-bot <tên_bot>`", room);
        }
        
        String botName = parts[1];
        boolean success = botManager.removeBot(botName);
        
        if (success) {
            return createBotResponse(
                String.format("✅ Bot '%s' đã được xóa thành công!", botName), 
                room
            );
        } else {
            return createBotResponse(
                String.format("❌ Không thể xóa bot '%s'. Bot có thể không tồn tại hoặc là bot hệ thống!", botName), 
                room
            );
        }
    }
    
    private ChatMessage handleListBots(String room) {
        var allBots = botManager.getAllBots();
        StringBuilder response = new StringBuilder();
        response.append("🤖 **DANH SÁCH TẤT CẢ BOT:**\n\n");
        
        // Bot hệ thống
        response.append("📋 **Bot hệ thống:**\n");
        allBots.entrySet().stream()
            .filter(entry -> !(entry.getValue() instanceof BotManager.CustomBot))
            .forEach(entry -> {
                Bot bot = entry.getValue();
                String status = bot.isActive() ? "🟢 Hoạt động" : "🔴 Tắt";
                response.append(String.format("• %s - %s (%s)\n", bot.getBotName(), bot.getDescription(), status));
            });
        
        response.append("\n🎨 **Bot tùy chỉnh:**\n");
        boolean hasCustomBots = false;
        for (var entry : allBots.entrySet()) {
            Bot bot = entry.getValue();
            if (bot instanceof BotManager.CustomBot) {
                hasCustomBots = true;
                String status = bot.isActive() ? "🟢 Hoạt động" : "🔴 Tắt";
                response.append(String.format("• %s - %s (%s)\n", bot.getBotName(), bot.getDescription(), status));
            }
        }
        
        if (!hasCustomBots) {
            response.append("• Chưa có bot tùy chỉnh nào\n");
        }
        
        response.append("\n💡 **Lệnh quản lý:**\n");
        response.append("• `/create-bot <tên> <mô tả>` - Tạo bot mới\n");
        response.append("• `/toggle-bot <tên>` - Bật/tắt bot\n");
        response.append("• `/delete-bot <tên>` - Xóa bot tùy chỉnh");
        
        return createBotResponse(response.toString(), room);
    }
    
    private ChatMessage handleToggleBot(String command, String room) {
        String[] parts = command.split("\\s+");
        if (parts.length < 2) {
            return createBotResponse("❌ Sử dụng: `/toggle-bot <tên_bot>`", room);
        }
        
        String botName = parts[1];
        boolean success = botManager.toggleBot(botName);
        
        if (success) {
            Bot bot = botManager.getBot(botName);
            String status = bot.isActive() ? "bật" : "tắt";
            return createBotResponse(
                String.format("✅ Bot '%s' đã được %s!", bot.getBotName(), status), 
                room
            );
        } else {
            return createBotResponse(
                String.format("❌ Không tìm thấy bot '%s'!", botName), 
                room
            );
        }
    }
    
    private String getUserRole(String username) {
        if (username.equals("admin")) return "ADMIN";
        if (username.equals("teacher01")) return "TEACHER";
        if (username.startsWith("sv")) return "STUDENT";
        return "USER";
    }
    
    private boolean hasPermission(String role) {
        return "ADMIN".equals(role) || "TEACHER".equals(role);
    }
}
