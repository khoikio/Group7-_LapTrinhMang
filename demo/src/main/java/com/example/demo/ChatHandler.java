package com.example.demo;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.CloseStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.demo.bot.BotManager;
import java.io.IOException;
import java.util.*;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatHandler extends TextWebSocketHandler {

    // Map lưu trữ các phòng chat: key = tên phòng, value = Set các session
    private final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();
    
    // Map lưu trữ session và phòng hiện tại của họ
    private final Map<WebSocketSession, String> sessionRooms = new ConcurrentHashMap<>();
    
    // Map lưu trữ session và nickname của họ
    private final Map<WebSocketSession, String> sessionNicknames = new ConcurrentHashMap<>();
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Bot Manager để xử lý các bot
    private final BotManager botManager = new BotManager();

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String payload = message.getPayload();
        
        // Handle heartbeat/ping messages for mobile connection stability
        if ("ping".equals(payload)) {
            session.sendMessage(new TextMessage("pong"));
            return;
        }
        
        if ("pong".equals(payload)) {
            // Client responded to our ping, connection is alive
            return;
        }
        
        System.out.println("Message received: " + payload);

        try {
            // Parse JSON message
            ChatMessage chatMessage = objectMapper.readValue(payload, ChatMessage.class);
            
            // Lưu nickname của session
            sessionNicknames.put(session, chatMessage.getNickname());
            
            // Xử lý lệnh /join
            if (chatMessage.getMessage().startsWith("/join ")) {
                String newRoomName = chatMessage.getMessage().substring(6).trim();
                if (!newRoomName.isEmpty()) {
                    joinRoom(session, newRoomName);
                    return;
                }
            }
            
            // Xử lý tin nhắn qua Bot Manager trước
            List<ChatMessage> botResponses = botManager.processMessage(chatMessage, session);
            
            // Nếu có bot response, gửi chúng
            for (ChatMessage botResponse : botResponses) {
                broadcastToRoom(botResponse.getRoom(), botResponse, session);
            }
            
            // Nếu user bị mute/ban, bot đã xử lý và return, không gửi tin nhắn gốc
            if (!botResponses.isEmpty() && 
                botResponses.stream().anyMatch(r -> r.getMessage().contains("bị cấm") || r.getMessage().contains("bị tắt tiếng"))) {
                return;
            }
            
            // Xử lý tin nhắn bình thường (nếu không phải bot command)
            String currentRoom = sessionRooms.get(session);
            if (currentRoom != null && !chatMessage.getMessage().startsWith("/")) {
                broadcastToRoom(currentRoom, chatMessage, session);
            }
            
        } catch (Exception e) {
            // Nếu không parse được JSON, xử lý như tin nhắn văn bản thường
            System.out.println("Failed to parse JSON, treating as plain text: " + e.getMessage());
            
            // Xử lý lệnh /join cho tin nhắn văn bản thường
            if (payload.startsWith("/join ")) {
                String newRoomName = payload.substring(6).trim();
                if (!newRoomName.isEmpty()) {
                    joinRoom(session, newRoomName);
                    return;
                }
            }
            
            // Gửi tin nhắn văn bản thường đến phòng hiện tại
            String currentRoom = sessionRooms.get(session);
            if (currentRoom != null) {
                String nickname = sessionNicknames.getOrDefault(session, "Anonymous");
                ChatMessage textMessage = new ChatMessage(nickname, payload, currentRoom);
                
                // Xử lý qua bot trước
                List<ChatMessage> botResponses = botManager.processMessage(textMessage, session);
                for (ChatMessage botResponse : botResponses) {
                    broadcastToRoom(botResponse.getRoom(), botResponse, session);
                }
                
                // Gửi tin nhắn gốc nếu không bị chặn
                if (botResponses.isEmpty() || 
                    !botResponses.stream().anyMatch(r -> r.getMessage().contains("bị cấm") || r.getMessage().contains("bị tắt tiếng"))) {
                    broadcastToRoom(currentRoom, textMessage, session);
                }
            }
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Tự động thêm vào phòng general
        joinRoom(session, "general");
        System.out.println("New connection: " + session.getId() + " joined room: general");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // Xóa session khỏi phòng hiện tại
        String currentRoom = sessionRooms.get(session);
        if (currentRoom != null) {
            leaveRoom(session, currentRoom);
        }
        
        // Xóa thông tin session
        sessionRooms.remove(session);
        sessionNicknames.remove(session);
        
        System.out.println("Connection closed: " + session.getId());
    }
    
    // Phương thức để join phòng
    private void joinRoom(WebSocketSession session, String roomName) throws IOException {
        // Rời phòng hiện tại (nếu có)
        String currentRoom = sessionRooms.get(session);
        if (currentRoom != null) {
            leaveRoom(session, currentRoom);
        }
        
        // Thêm vào phòng mới
        rooms.computeIfAbsent(roomName, k -> ConcurrentHashMap.newKeySet()).add(session);
        sessionRooms.put(session, roomName);
        
        // Thông báo cho user
        String nickname = sessionNicknames.getOrDefault(session, "Anonymous");
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
            new ChatMessage("System", "Bạn đã tham gia phòng: " + roomName, roomName)
        )));
        
        // Thông báo cho các user khác trong phòng
        ChatMessage joinMessage = new ChatMessage("System", nickname + " đã tham gia phòng", roomName);
        broadcastToRoom(roomName, joinMessage, session);
        
        System.out.println("Session " + session.getId() + " joined room: " + roomName);
    }
    
    // Phương thức để rời phòng
    private void leaveRoom(WebSocketSession session, String roomName) throws IOException {
        Set<WebSocketSession> roomSessions = rooms.get(roomName);
        if (roomSessions != null) {
            roomSessions.remove(session);
            
            // Xóa phòng nếu không còn ai
            if (roomSessions.isEmpty()) {
                rooms.remove(roomName);
            } else {
                // Thông báo cho các user khác trong phòng
                String nickname = sessionNicknames.getOrDefault(session, "Anonymous");
                ChatMessage leaveMessage = new ChatMessage("System", nickname + " đã rời phòng", roomName);
                broadcastToRoom(roomName, leaveMessage, session);
            }
        }
    }
    
    // Phương thức broadcast tin nhắn đến tất cả user trong phòng
    private synchronized void broadcastToRoom(String roomName, ChatMessage message, WebSocketSession sender) throws IOException {
        Set<WebSocketSession> roomSessions = rooms.get(roomName);
        if (roomSessions != null) {
            String jsonMessage = objectMapper.writeValueAsString(message);
            
            // Gửi đến tất cả session trong phòng (bao gồm cả người gửi)
            // Sử dụng copy để tránh concurrent modification
            Set<WebSocketSession> sessionsCopy = new HashSet<>(roomSessions);
            for (WebSocketSession session : sessionsCopy) {
                try {
                    if (session.isOpen()) {
                        synchronized (session) {
                            session.sendMessage(new TextMessage(jsonMessage));
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error sending message to session: " + e.getMessage());
                    // Remove broken session
                    roomSessions.remove(session);
                    sessionRooms.remove(session);
                    sessionNicknames.remove(session);
                }
            }
        }
    }
}