package com.example.demo.moderation;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class ModerationService {
    
    // Lưu trữ thông tin user bị mute
    private Map<String, MuteInfo> mutedUsers = new ConcurrentHashMap<>();
    // Lưu trữ user bị ban vĩnh viễn
    private Map<String, BanInfo> bannedUsers = new ConcurrentHashMap<>();
    
    // Kiểm tra user có bị mute không
    public boolean isMuted(String username) {
        MuteInfo muteInfo = mutedUsers.get(username);
        if (muteInfo == null) return false;
        
        if (muteInfo.isExpired()) {
            mutedUsers.remove(username);
            return false;
        }
        return true;
    }
    
    // Kiểm tra user có bị ban không
    public boolean isBanned(String username) {
        return bannedUsers.containsKey(username);
    }
    
    // Mute user trong thời gian nhất định
    public MuteResult muteUser(String username, int minutes, String reason, String moderator) {
        if (isBanned(username)) {
            return new MuteResult(false, "User đã bị ban vĩnh viễn");
        }
        
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(minutes);
        MuteInfo muteInfo = new MuteInfo(username, reason, moderator, expiryTime);
        mutedUsers.put(username, muteInfo);
        
        return new MuteResult(true, String.format("User @%s đã bị tắt tiếng %d phút", username, minutes));
    }
    
    // Ban user vĩnh viễn
    public BanResult banUser(String username, String reason, String moderator) {
        BanInfo banInfo = new BanInfo(username, reason, moderator, LocalDateTime.now());
        bannedUsers.put(username, banInfo);
        
        // Remove từ mute list nếu có
        mutedUsers.remove(username);
        
        return new BanResult(true, String.format("User @%s đã bị cấm chat vĩnh viễn", username));
    }
    
    // Unmute user
    public boolean unmuteUser(String username) {
        return mutedUsers.remove(username) != null;
    }
    
    // Unban user
    public boolean unbanUser(String username) {
        return bannedUsers.remove(username) != null;
    }
    
    // Lấy thông tin mute
    public MuteInfo getMuteInfo(String username) {
        return mutedUsers.get(username);
    }
    
    // Lấy thông tin ban
    public BanInfo getBanInfo(String username) {
        return bannedUsers.get(username);
    }
    
    // Lấy thời gian còn lại của mute
    public String getRemainingMuteTime(String username) {
        MuteInfo muteInfo = mutedUsers.get(username);
        if (muteInfo == null || muteInfo.isExpired()) {
            return null;
        }
        
        long minutesLeft = java.time.Duration.between(LocalDateTime.now(), muteInfo.expiryTime).toMinutes();
        if (minutesLeft <= 0) {
            mutedUsers.remove(username);
            return null;
        }
        
        if (minutesLeft < 60) {
            return minutesLeft + " phút";
        } else {
            long hours = minutesLeft / 60;
            long mins = minutesLeft % 60;
            return hours + " giờ " + mins + " phút";
        }
    }
    
    // Inner classes
    public static class MuteInfo {
        public String username;
        public String reason;
        public String moderator;
        public LocalDateTime muteTime;
        public LocalDateTime expiryTime;
        
        public MuteInfo(String username, String reason, String moderator, LocalDateTime expiryTime) {
            this.username = username;
            this.reason = reason;
            this.moderator = moderator;
            this.muteTime = LocalDateTime.now();
            this.expiryTime = expiryTime;
        }
        
        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiryTime);
        }
    }
    
    public static class BanInfo {
        public String username;
        public String reason;
        public String moderator;
        public LocalDateTime banTime;
        
        public BanInfo(String username, String reason, String moderator, LocalDateTime banTime) {
            this.username = username;
            this.reason = reason;
            this.moderator = moderator;
            this.banTime = banTime;
        }
    }
    
    public static class MuteResult {
        public boolean success;
        public String message;
        
        public MuteResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
    
    public static class BanResult {
        public boolean success;
        public String message;
        
        public BanResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}
