package com.example.demo.bot;

import com.example.demo.ChatMessage;
import com.example.demo.moderation.ModerationService;
import org.springframework.web.socket.WebSocketSession;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModerationBot extends Bot {
    
    private ModerationService moderationService;
    private Pattern mutePattern = Pattern.compile("/mute\\s+@(\\w+)\\s+(\\d+)(?:\\s+(.+))?");
    private Pattern banPattern = Pattern.compile("/ban\\s+@(\\w+)(?:\\s+(.+))?");
    private Pattern reportPattern = Pattern.compile("/report\\s+@(\\w+)(?:\\s+(.+))?");
    
    public ModerationBot(ModerationService moderationService) {
        super("ModerationBot", "Bot quản lý và điều hành phòng chat");
        this.moderationService = moderationService;
    }

    @Override
    public boolean canHandle(ChatMessage message) {
        String msg = message.getMessage().toLowerCase();
        return msg.startsWith("/mute") || msg.startsWith("/ban") || 
               msg.startsWith("/unmute") || msg.startsWith("/unban") ||
               msg.startsWith("/report") || msg.startsWith("/check");
    }

    @Override
    public ChatMessage processMessage(ChatMessage message, WebSocketSession session) {
        String command = message.getMessage().trim();
        String userRole = getUserRole(message.getNickname()); // Lấy role của user
        
        // Kiểm tra quyền hạn
        if (command.startsWith("/mute") || command.startsWith("/ban") || 
            command.startsWith("/unmute") || command.startsWith("/unban")) {
            if (!hasModeratorPermission(userRole)) {
                return createBotResponse(
                    "❌ Bạn không có quyền sử dụng lệnh này. Chỉ **Giáo viên** và **Admin** mới có thể điều hành.", 
                    message.getRoom()
                );
            }
        }
        
        if (command.startsWith("/mute")) {
            return handleMuteCommand(command, message.getNickname(), message.getRoom());
        } else if (command.startsWith("/ban")) {
            return handleBanCommand(command, message.getNickname(), message.getRoom());
        } else if (command.startsWith("/unmute")) {
            return handleUnmuteCommand(command, message.getRoom());
        } else if (command.startsWith("/unban")) {
            return handleUnbanCommand(command, message.getRoom());
        } else if (command.startsWith("/report")) {
            return handleReportCommand(command, message.getNickname(), message.getRoom());
        } else if (command.startsWith("/check")) {
            return handleCheckCommand(command, message.getRoom());
        }
        
        return null;
    }
    
    private ChatMessage handleMuteCommand(String command, String moderator, String room) {
        Matcher matcher = mutePattern.matcher(command);
        if (!matcher.matches()) {
            return createBotResponse(
                "❌ Sử dụng: `/mute @username <phút> [lý do]`\nVí dụ: `/mute @sv01 5 spam tin nhắn`", 
                room
            );
        }
        
        String username = matcher.group(1);
        int minutes = Integer.parseInt(matcher.group(2));
        String reason = matcher.group(3) != null ? matcher.group(3) : "Không nêu lý do";
        
        // Kiểm tra không tự mute chính mình
        if (username.equals(moderator)) {
            return createBotResponse("❌ Bạn không thể tắt tiếng chính mình!", room);
        }
        
        // Kiểm tra không mute admin/teacher khác
        String targetRole = getUserRole(username);
        if (hasModeratorPermission(targetRole)) {
            return createBotResponse("❌ Không thể tắt tiếng Giáo viên hoặc Admin khác!", room);
        }
        
        ModerationService.MuteResult result = moderationService.muteUser(username, minutes, reason, moderator);
        
        String response = String.format("""
            🔇 **USER ĐÃ BỊ TẮT TIẾNG**
            
            👤 User: @%s
            ⏰ Thời gian: %d phút
            📝 Lý do: %s
            👨‍🏫 Người thực hiện: @%s
            
            ⚠️ @%s sẽ không thể gửi tin nhắn trong %d phút.
            """, username, minutes, reason, moderator, username, minutes);
            
        return createBotResponse(response, room);
    }
    
    private ChatMessage handleBanCommand(String command, String moderator, String room) {
        Matcher matcher = banPattern.matcher(command);
        if (!matcher.matches()) {
            return createBotResponse(
                "❌ Sử dụng: `/ban @username [lý do]`\nVí dụ: `/ban @sv01 vi phạm nội quy nghiêm trọng`", 
                room
            );
        }
        
        String username = matcher.group(1);
        String reason = matcher.group(2) != null ? matcher.group(2) : "Không nêu lý do";
        
        // Các kiểm tra tương tự như mute
        if (username.equals(moderator)) {
            return createBotResponse("❌ Bạn không thể cấm chính mình!", room);
        }
        
        String targetRole = getUserRole(username);
        if (hasModeratorPermission(targetRole)) {
            return createBotResponse("❌ Không thể cấm Giáo viên hoặc Admin khác!", room);
        }
        
        moderationService.banUser(username, reason, moderator);
        
        String response = String.format("""
            🚫 **USER ĐÃ BỊ CẤM VĨNH VIỄN**
            
            👤 User: @%s
            📝 Lý do: %s
            👨‍🏫 Người thực hiện: @%s
            
            ⛔ @%s đã bị cấm chat vĩnh viễn và không thể tham gia bất kỳ phòng nào.
            """, username, reason, moderator, username);
            
        return createBotResponse(response, room);
    }
    
    private ChatMessage handleReportCommand(String command, String reporter, String room) {
        Matcher matcher = reportPattern.matcher(command);
        if (!matcher.matches()) {
            return createBotResponse(
                "❌ Sử dụng: `/report @username [lý do]`\nVí dụ: `/report @sv01 spam tin nhắn`", 
                room
            );
        }
        
        String reportedUser = matcher.group(1);
        String reason = matcher.group(2) != null ? matcher.group(2) : "Không nêu lý do cụ thể";
        
        String response = String.format("""
            📋 **BÁO CÁO VI PHẠM**
            
            👤 Người báo cáo: @%s
            🎯 Người bị báo cáo: @%s
            📝 Lý do: %s
            🕐 Thời gian: %s
            
            ✅ **Báo cáo đã được ghi nhận!**
            📢 Đã thông báo tới tất cả **Giáo viên** và **Admin** trong hệ thống.
            🔍 Các moderator sẽ xem xét và xử lý trong thời gian sớm nhất.
            
            💡 Cảm ơn bạn đã giúp duy trì môi trường chat tích cực!
            """, reporter, reportedUser, reason, java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")));
            
        return createBotResponse(response, room);
    }
    
    private ChatMessage handleCheckCommand(String command, String room) {
        String[] parts = command.split("\\s+");
        if (parts.length < 2 || !parts[1].startsWith("@")) {
            return createBotResponse("❌ Sử dụng: `/check @username`", room);
        }
        
        String username = parts[1].substring(1);
        StringBuilder status = new StringBuilder();
        status.append(String.format("📊 **TRẠNG THÁI CỦA @%s:**\n\n", username));
        
        if (moderationService.isBanned(username)) {
            ModerationService.BanInfo banInfo = moderationService.getBanInfo(username);
            status.append(String.format("""
                🚫 **BỊ CẤM VĨNH VIỄN**
                📝 Lý do: %s
                👨‍🏫 Người cấm: @%s
                🕐 Thời gian: %s
                """, banInfo.reason, banInfo.moderator, 
                banInfo.banTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"))));
        } else if (moderationService.isMuted(username)) {
            ModerationService.MuteInfo muteInfo = moderationService.getMuteInfo(username);
            String timeLeft = moderationService.getRemainingMuteTime(username);
            status.append(String.format("""
                🔇 **BỊ TẮT TIẾNG**
                📝 Lý do: %s
                👨‍🏫 Người tắt tiếng: @%s
                ⏰ Thời gian còn lại: %s
                """, muteInfo.reason, muteInfo.moderator, timeLeft));
        } else {
            status.append("✅ **HOẠT ĐỘNG BÌNH THƯỜNG**\nKhông có hạn chế nào.");
        }
        
        return createBotResponse(status.toString(), room);
    }
    
    private ChatMessage handleUnmuteCommand(String command, String room) {
        String[] parts = command.split("\\s+");
        if (parts.length < 2 || !parts[1].startsWith("@")) {
            return createBotResponse("❌ Sử dụng: `/unmute @username`", room);
        }
        
        String username = parts[1].substring(1);
        boolean success = moderationService.unmuteUser(username);
        
        if (success) {
            return createBotResponse(
                String.format("✅ @%s đã được bỏ tắt tiếng và có thể chat trở lại.", username), 
                room
            );
        } else {
            return createBotResponse(
                String.format("❌ @%s không bị tắt tiếng hoặc đã hết thời gian tắt tiếng.", username), 
                room
            );
        }
    }
    
    private ChatMessage handleUnbanCommand(String command, String room) {
        String[] parts = command.split("\\s+");
        if (parts.length < 2 || !parts[1].startsWith("@")) {
            return createBotResponse("❌ Sử dụng: `/unban @username`", room);
        }
        
        String username = parts[1].substring(1);
        boolean success = moderationService.unbanUser(username);
        
        if (success) {
            return createBotResponse(
                String.format("✅ @%s đã được bỏ cấm và có thể tham gia chat trở lại.", username), 
                room
            );
        } else {
            return createBotResponse(
                String.format("❌ @%s không bị cấm.", username), 
                room
            );
        }
    }
    
    // Helper methods
    private String getUserRole(String username) {
        if (username.equals("admin")) return "ADMIN";
        if (username.equals("teacher01")) return "TEACHER";
        if (username.startsWith("sv")) return "STUDENT";
        return "USER";
    }
    
    private boolean hasModeratorPermission(String role) {
        return "ADMIN".equals(role) || "TEACHER".equals(role);
    }
    
    // Public method để kiểm tra từ bên ngoài
    public boolean canUserSendMessage(String username) {
        return !moderationService.isMuted(username) && !moderationService.isBanned(username);
    }
    
    public String getUserModerationStatus(String username) {
        if (moderationService.isBanned(username)) {
            return "BANNED";
        } else if (moderationService.isMuted(username)) {
            return "MUTED";
        }
        return "ACTIVE";
    }
}
