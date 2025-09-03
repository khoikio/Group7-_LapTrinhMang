package com.example.demo.bot;

import com.example.demo.ChatMessage;
import org.springframework.web.socket.WebSocketSession;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpamDetectionBot extends Bot {
    
    // Theo dõi tin nhắn của từng user
    private Map<String, List<Long>> userMessageTimes = new ConcurrentHashMap<>();
    private Map<String, Integer> userSpamCount = new ConcurrentHashMap<>();
    
    // Cài đặt spam detection
    private static final int MAX_MESSAGES_PER_MINUTE = 5; // Tối đa 5 tin nhắn/phút
    private static final int SPAM_THRESHOLD = 3; // 3 lần spam = cảnh báo
    private static final long MINUTE_IN_MILLIS = 60 * 1000;
    
    public SpamDetectionBot() {
        super("SpamDetector", "Bot phát hiện và báo cáo spam");
    }

    @Override
    public boolean canHandle(ChatMessage message) {
        // Bot này xử lý tất cả tin nhắn để detect spam
        return !message.getNickname().startsWith("🤖");
    }

    @Override
    public ChatMessage processMessage(ChatMessage message, WebSocketSession session) {
        String username = message.getNickname();
        long currentTime = System.currentTimeMillis();
        
        // Lấy danh sách thời gian tin nhắn của user
        userMessageTimes.computeIfAbsent(username, k -> new ArrayList<>()).add(currentTime);
        
        // Xóa tin nhắn cũ hơn 1 phút
        List<Long> messageTimes = userMessageTimes.get(username);
        messageTimes.removeIf(time -> currentTime - time > MINUTE_IN_MILLIS);
        
        // Kiểm tra spam
        if (messageTimes.size() > MAX_MESSAGES_PER_MINUTE) {
            return handleSpamDetected(username, message.getRoom());
        }
        
        // Kiểm tra lệnh report spam
        if (message.getMessage().startsWith("/spam @")) {
            return handleSpamReport(message);
        }
        
        return null; // Không có response nếu không phát hiện spam
    }
    
    private ChatMessage handleSpamDetected(String username, String room) {
        int spamCount = userSpamCount.getOrDefault(username, 0) + 1;
        userSpamCount.put(username, spamCount);
        
        String response;
        if (spamCount == 1) {
            response = String.format("⚠️ **CẢNH BÁO SPAM**\n@%s đang gửi tin nhắn quá nhanh! Vui lòng chậm lại. 🐌", username);
        } else if (spamCount >= SPAM_THRESHOLD) {
            response = String.format("""
                🚨 **BÁO CÁO SPAM NGHIÊM TRỌNG**
                User: @%s
                Số lần vi phạm: %d
                
                📢 Đã thông báo cho **Giáo viên** và **Quản trị viên**!
                Yêu cầu xem xét tắt tiếng hoặc cấm chat.
                """, username, spamCount);
        } else {
            response = String.format("⚠️ @%s tiếp tục spam (%d/%d). Hãy chú ý!", username, spamCount, SPAM_THRESHOLD);
        }
        
        return createBotResponse(response, room);
    }
    
    private ChatMessage handleSpamReport(ChatMessage message) {
        // Xử lý lệnh /spam @username
        String[] parts = message.getMessage().split("\\s+");
        if (parts.length < 2 || !parts[1].startsWith("@")) {
            return createBotResponse("❌ Sử dụng: `/spam @username` để báo cáo spam", message.getRoom());
        }
        
        String reportedUser = parts[1].substring(1); // Bỏ ký tự @
        String reporter = message.getNickname();
        
        String response = String.format("""
            📋 **BÁO CÁO SPAM**
            Người báo cáo: @%s
            Người bị báo cáo: @%s
            
            ✅ Báo cáo đã được gửi đến **Giáo viên** và **Quản trị viên**.
            Họ sẽ xem xét và xử lý phù hợp.
            
            💡 Tip: Bạn cũng có thể sử dụng `/report @user lý_do` để báo cáo chi tiết hơn.
            """, reporter, reportedUser);
            
        return createBotResponse(response, message.getRoom());
    }
    
    // Phương thức reset spam count (có thể gọi từ bên ngoài)
    public void resetSpamCount(String username) {
        userSpamCount.remove(username);
        userMessageTimes.remove(username);
    }
    
    // Lấy thông tin spam của user
    public int getSpamCount(String username) {
        return userSpamCount.getOrDefault(username, 0);
    }
}
