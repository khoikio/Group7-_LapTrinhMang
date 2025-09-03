package com.example.demo.bot;

import com.example.demo.ChatMessage;
import org.springframework.web.socket.WebSocketSession;

public class HelpBot extends Bot {
    
    public HelpBot() {
        super("HelpBot", "Bot hỗ trợ thông tin và nội quy");
    }

    @Override
    public boolean canHandle(ChatMessage message) {
        String msg = message.getMessage().toLowerCase();
        return msg.startsWith("/help") || msg.startsWith("/nội quy") || 
               msg.startsWith("/hướng dẫn") || msg.contains("help");
    }

    @Override
    public ChatMessage processMessage(ChatMessage message, WebSocketSession session) {
        String command = message.getMessage().toLowerCase().trim();
        String response = "";

        if (command.startsWith("/help")) {
            response = getHelpMessage();
        } else if (command.startsWith("/nội quy")) {
            response = getRulesMessage();
        } else if (command.startsWith("/hướng dẫn")) {
            response = getGuideMessage();
        } else if (message.getMessage().toLowerCase().contains("help")) {
            response = "Bạn cần hỗ trợ? Gõ /help để xem danh sách lệnh! 😊";
        }

        return createBotResponse(response, message.getRoom());
    }

    private String getHelpMessage() {
        return """
        📚 **DANH SÁCH LỆNH HỖ TRỢ:**
        
        🔹 `/help` - Hiển thị danh sách lệnh
        🔹 `/nội quy` - Xem nội quy phòng chat
        🔹 `/hướng dẫn` - Hướng dẫn sử dụng
        🔹 `/join <tên_phòng>` - Tham gia phòng mới
        
        🎮 **LỆNH GIẢI TRÍ:**
        🔹 `/quiz` - Chơi trò quiz
        🔹 `/spam @user` - Báo cáo spam
        🔹 `/report @user <lý do>` - Báo cáo vi phạm
        
        👨‍🏫 **LỆNH CHO GIÁO VIÊN/ADMIN:**
        🔹 `/mute @user <phút> [lý do]` - Tắt tiếng user
        🔹 `/unmute @user` - Bỏ tắt tiếng
        🔹 `/ban @user [lý do]` - Cấm user vĩnh viễn
        🔹 `/unban @user` - Bỏ cấm user
        🔹 `/check @user` - Kiểm tra trạng thái user
        
        🤖 **QUẢN LÝ BOT:**
        🔹 `/create-bot <tên> <mô tả>` - Tạo bot mới
        🔹 `/list-bots` - Xem danh sách bot
        🔹 `/toggle-bot <tên>` - Bật/tắt bot
        🔹 `/delete-bot <tên>` - Xóa bot tùy chỉnh
        """;
    }

    private String getRulesMessage() {
        return """
        📋 **NỘI QUY PHÒNG CHAT:**
        
        ✅ **ĐƯỢC PHÉP:**
        • Chat lịch sự, tôn trọng mọi người
        • Thảo luận về học tập
        • Chia sẻ kiến thức hữu ích
        • Sử dụng emoji phù hợp
        
        ❌ **KHÔNG ĐƯỢC PHÉP:**
        • Spam tin nhắn
        • Sử dụng ngôn từ thô tục
        • Chia sẻ nội dung không phù hợp
        • Quấy rối người khác
        
        ⚠️ **VI PHẠM SẼ BỊ:**
        • Cảnh cáo lần đầu
        • Tắt tiếng 5-30 phút
        • Cấm chat tạm thời/vĩnh viễn
        """;
    }

    private String getGuideMessage() {
        return """
        📖 **HƯỚNG DẪN SỬ DỤNG:**
        
        1️⃣ **Tham gia phòng:**
           - Chọn phòng từ sidebar bên trái
           - Hoặc gõ `/join tên_phòng`
        
        2️⃣ **Gửi tin nhắn:**
           - Gõ tin nhắn và nhấn Enter
           - Sử dụng emoji để sinh động hơn
        
        3️⃣ **Báo cáo vi phạm:**
           - Gõ `/report @username lý_do`
           - Giáo viên sẽ được thông báo
        
        4️⃣ **Chơi game:**
           - Gõ `/quiz` để chơi câu đố
           - Gõ `/game` để xem game khác
        """;
    }
}
