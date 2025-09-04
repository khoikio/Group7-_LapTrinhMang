package com.example.demo.bot;

import com.example.demo.ChatMessage;
import org.springframework.web.socket.WebSocketSession;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class QuizBot extends Bot {
    
    private Map<String, QuizGameState> activeGames = new ConcurrentHashMap<>();
    private List<QuizQuestion> questionBank = new ArrayList<>();
    private Timer gameTimer = new Timer();
    
    public QuizBot() {
        super("QuizBot", "Bot tổ chức trò chơi câu đố với điểm số");
        initializeQuestions();
    }

    @Override
    public boolean canHandle(ChatMessage message) {
        String msg = message.getMessage().toLowerCase().trim();
        return msg.startsWith("/quiz") || hasActiveGame(message.getRoom());
    }

    @Override
    public ChatMessage processMessage(ChatMessage message, WebSocketSession session) {
        String command = message.getMessage().toLowerCase().trim();
        String room = message.getRoom();
        String username = message.getNickname();
        
        if (command.startsWith("/quiz start")) {
            return startQuizGame(room, username);
        } else if (command.startsWith("/quiz stop")) {
            return stopQuizGame(room, username);
        } else if (command.startsWith("/quiz answer ")) {
            String answer = command.substring(13).trim(); // "/quiz answer ".length() = 13
            return handleQuizAnswer(room, username, answer);
        } else if (command.startsWith("/quiz score")) {
            return showScores(room);
        } else if (command.equals("/quiz")) {
            return showQuizHelp(room);
        }
        
        return null;
    }
    
    private ChatMessage startQuizGame(String room, String username) {
        if (activeGames.containsKey(room)) {
            return createBotResponse("🎯 Quiz đang diễn ra! Gõ `/quiz stop` để kết thúc game hiện tại.", room);
        }
        
        QuizGameState gameState = new QuizGameState(username);
        activeGames.put(room, gameState);
        
        return nextQuestion(room);
    }
    
    private ChatMessage stopQuizGame(String room, String username) {
        QuizGameState game = activeGames.get(room);
        if (game == null) {
            return createBotResponse("❌ Không có quiz nào đang diễn ra!", room);
        }
        
        // Chỉ game master hoặc admin mới có thể stop
        if (!game.isGameMaster(username) && !isAdmin(username)) {
            return createBotResponse("❌ Chỉ người tạo game hoặc admin mới có thể dừng quiz!", room);
        }
        
        String finalResults = getFinalResults(room);
        activeGames.remove(room);
        
        return createBotResponse(finalResults, room);
    }
    
    private ChatMessage handleQuizAnswer(String room, String username, String answer) {
        QuizGameState game = activeGames.get(room);
        if (game == null) {
            return createBotResponse("❌ Không có quiz nào đang diễn ra! Gõ `/quiz start` để bắt đầu.", room);
        }
        
        if (game.hasAnswered(username)) {
            return createBotResponse(String.format("⚠️ @%s đã trả lời rồi! Chờ câu hỏi tiếp theo.", username), room);
        }
        
        boolean isCorrect = game.checkAnswer(username, answer);
        game.recordAnswer(username, answer, isCorrect);
        
        String response;
        if (isCorrect) {
            int newScore = game.getScore(username);
            response = String.format("🎉 **CHÍNH XÁC!** @%s trả lời đúng! (+10 điểm)\n💯 Điểm hiện tại: %d", username, newScore);
        } else {
            response = String.format("❌ @%s trả lời sai. Đáp án: **%s**", username, game.getCurrentQuestion().correctAnswer.toUpperCase());
        }
        
        // Auto next question sau 3 giây nếu đã có người trả lời đúng
        if (isCorrect) {
            scheduleNextQuestion(room, 3000);
        }
        
        return createBotResponse(response, room);
    }
    
    private ChatMessage nextQuestion(String room) {
        QuizGameState game = activeGames.get(room);
        if (game == null) return null;
        
        if (game.getQuestionNumber() >= 10) {
            // Kết thúc game sau 10 câu
            String finalResults = getFinalResults(room);
            activeGames.remove(room);
            return createBotResponse(finalResults, room);
        }
        
        QuizQuestion question = getRandomQuestion();
        game.setCurrentQuestion(question);
        game.nextQuestion();
        game.clearAnswers();
        
        String response = String.format("""
            🎯 **QUIZ GAME - Câu %d/10**
            
            ❓ **%s**
            
            🅰️ A) %s
            🅱️ B) %s  
            🅲️ C) %s
            🅳️ D) %s
            
            💡 **Trả lời:** `/quiz answer A` (hoặc B, C, D)
            ⏰ **Thời gian:** 30 giây
            🏆 **Điểm thưởng:** 10 điểm cho câu trả lời đúng
            
            📊 **Bảng điểm hiện tại:**
            %s
            """, 
            game.getQuestionNumber(),
            question.question,
            question.optionA,
            question.optionB, 
            question.optionC,
            question.optionD,
            getScoreBoard(game)
        );
        
        // Auto next question sau 30 giây
        scheduleNextQuestion(room, 30000);
        
        return createBotResponse(response, room);
    }
    
    private void scheduleNextQuestion(String room, long delay) {
        gameTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                QuizGameState game = activeGames.get(room);
                if (game != null) {
                    // Broadcast next question (cần ChatHandler hỗ trợ)
                    // Tạm thời chỉ log
                    System.out.println("Auto next question for room: " + room);
                }
            }
        }, delay);
    }
    
    private ChatMessage showScores(String room) {
        QuizGameState game = activeGames.get(room);
        if (game == null) {
            return createBotResponse("❌ Không có quiz nào đang diễn ra!", room);
        }
        
        String response = String.format("""
            📊 **BẢNG ĐIỂM QUIZ**
            
            %s
            
            🎯 Câu hỏi hiện tại: %d/10
            ⏰ Gõ `/quiz answer <A/B/C/D>` để trả lời
            """, 
            getScoreBoard(game),
            game.getQuestionNumber()
        );
        
        return createBotResponse(response, room);
    }
    
    private ChatMessage showQuizHelp(String room) {
        return createBotResponse("""
            🎯 **HƯỚNG DẪN QUIZ GAME**
            
            🚀 **Bắt đầu:** `/quiz start`
            ✋ **Dừng game:** `/quiz stop`
            📝 **Trả lời:** `/quiz answer A` (hoặc B, C, D)
            📊 **Xem điểm:** `/quiz score`
            
            🏆 **Luật chơi:**
            • 10 câu hỏi về Java/Spring Boot
            • Mỗi câu đúng: +10 điểm
            • Thời gian: 30 giây/câu
            • Người cao điểm nhất thắng!
            
            💡 **Mẹo:** Trả lời nhanh để có lợi thế!
            """, room);
    }
    
    private String getScoreBoard(QuizGameState game) {
        if (game.getScores().isEmpty()) {
            return "• Chưa có ai tham gia";
        }
        
        StringBuilder sb = new StringBuilder();
        game.getScores().entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(entry -> {
                String medal = entry.getValue() > 0 ? "🏆" : "📝";
                sb.append(String.format("• %s @%s: %d điểm\n", medal, entry.getKey(), entry.getValue()));
            });
        
        return sb.toString().trim();
    }
    
    private String getFinalResults(String room) {
        QuizGameState game = activeGames.get(room);
        if (game == null) return "Game không tồn tại.";
        
        var sortedScores = game.getScores().entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .toList();
        
        StringBuilder results = new StringBuilder();
        results.append("🏁 **KẾT THÚC QUIZ GAME!**\n\n");
        results.append("🏆 **BẢNG XẾP HẠNG CUỐI CÙNG:**\n");
        
        for (int i = 0; i < sortedScores.size(); i++) {
            var entry = sortedScores.get(i);
            String medal = switch (i) {
                case 0 -> "🥇";
                case 1 -> "🥈"; 
                case 2 -> "🥉";
                default -> "🏅";
            };
            results.append(String.format("%s **#%d** @%s - %d điểm\n", medal, i+1, entry.getKey(), entry.getValue()));
        }
        
        if (!sortedScores.isEmpty()) {
            var winner = sortedScores.get(0);
            results.append(String.format("\n🎉 **CHÚC MỪNG @%s ĐÃ CHIẾN THẮNG!**\n", winner.getKey()));
            results.append(String.format("💯 Điểm số: %d/%d\n", winner.getValue(), game.getQuestionNumber() * 10));
        }
        
        results.append("\n💡 Gõ `/quiz start` để chơi lại!");
        
        return results.toString();
    }
    
    private boolean hasActiveGame(String room) {
        return activeGames.containsKey(room);
    }
    
    private boolean isAdmin(String username) {
        return username.equals("admin") || username.equals("teacher01");
    }
    
    private QuizQuestion getRandomQuestion() {
        Random random = new Random();
        return questionBank.get(random.nextInt(questionBank.size()));
    }
    
    private void initializeQuestions() {
        questionBank.add(new QuizQuestion(
            "Java là ngôn ngữ lập trình thuộc loại nào?",
            "Ngôn ngữ thông dịch", 
            "Ngôn ngữ biên dịch",
            "Ngôn ngữ biên dịch và thông dịch", 
            "Ngôn ngữ máy",
            "C",
            "Java được biên dịch thành bytecode và sau đó được thông dịch bởi JVM"
        ));
        
        questionBank.add(new QuizQuestion(
            "Từ khóa nào được dùng để kế thừa trong Java?",
            "implements", 
            "extends", 
            "inherits", 
            "super",
            "B",
            "Từ khóa 'extends' được sử dụng để kế thừa từ một class khác"
        ));
        
        questionBank.add(new QuizQuestion(
            "Spring Boot là gì?",
            "Một IDE", 
            "Một framework", 
            "Một database", 
            "Một web server",
            "B", 
            "Spring Boot là một framework Java giúp đơn giản hóa việc phát triển ứng dụng Spring"
        ));
        
        questionBank.add(new QuizQuestion(
            "WebSocket được sử dụng để làm gì?",
            "Gửi email", 
            "Lưu trữ dữ liệu", 
            "Giao tiếp real-time", 
            "Tạo database",
            "C",
            "WebSocket cho phép giao tiếp hai chiều real-time giữa client và server"
        ));
        
        questionBank.add(new QuizQuestion(
            "Annotation nào được sử dụng để đánh dấu một class là Spring Boot Application?",
            "@SpringBootApplication", 
            "@SpringApp", 
            "@BootApplication", 
            "@Application",
            "A",
            "@SpringBootApplication kết hợp @Configuration, @EnableAutoConfiguration và @ComponentScan"
        ));
        
        questionBank.add(new QuizQuestion(
            "Phương thức HTTP nào được sử dụng để tạo mới resource?",
            "GET", 
            "POST", 
            "PUT", 
            "DELETE",
            "B",
            "POST được sử dụng để tạo mới resource trong RESTful API"
        ));
        
        questionBank.add(new QuizQuestion(
            "JVM viết tắt của từ gì?",
            "Java Virtual Machine", 
            "Java Variable Method", 
            "Java Verified Module", 
            "Java Version Manager",
            "A",
            "JVM (Java Virtual Machine) là máy ảo thực thi bytecode Java"
        ));
        
        questionBank.add(new QuizQuestion(
            "Cổng mặc định của Spring Boot web application là bao nhiêu?",
            "8000", 
            "8080", 
            "9000", 
            "3000",
            "B",
            "Spring Boot web application mặc định chạy trên port 8080"
        ));
    }
    
    // Inner classes
    private static class QuizGameState {
        private String gameMaster;
        private QuizQuestion currentQuestion;
        private Map<String, Integer> scores = new HashMap<>();
        private Map<String, String> currentAnswers = new HashMap<>();
        private int questionNumber = 0;
        
        public QuizGameState(String gameMaster) {
            this.gameMaster = gameMaster;
        }
        
        public void setCurrentQuestion(QuizQuestion question) {
            this.currentQuestion = question;
        }
        
        public QuizQuestion getCurrentQuestion() {
            return currentQuestion;
        }
        
        public boolean checkAnswer(String username, String answer) {
            if (currentQuestion == null) return false;
            return currentQuestion.correctAnswer.equalsIgnoreCase(answer.trim());
        }
        
        public void recordAnswer(String username, String answer, boolean isCorrect) {
            currentAnswers.put(username, answer);
            if (isCorrect) {
                scores.put(username, scores.getOrDefault(username, 0) + 10);
            } else {
                scores.putIfAbsent(username, 0);
            }
        }
        
        public boolean hasAnswered(String username) {
            return currentAnswers.containsKey(username);
        }
        
        public void clearAnswers() {
            currentAnswers.clear();
        }
        
        public void nextQuestion() {
            questionNumber++;
        }
        
        public int getQuestionNumber() {
            return questionNumber;
        }
        
        public int getScore(String username) {
            return scores.getOrDefault(username, 0);
        }
        
        public Map<String, Integer> getScores() {
            return scores;
        }
        
        public boolean isGameMaster(String username) {
            return gameMaster.equals(username);
        }
    }
    
    private static class QuizQuestion {
        String question;
        String optionA, optionB, optionC, optionD;
        String correctAnswer;
        String explanation;
        
        QuizQuestion(String question, String optionA, String optionB, String optionC, String optionD, 
                    String correctAnswer, String explanation) {
            this.question = question;
            this.optionA = optionA;
            this.optionB = optionB;
            this.optionC = optionC;
            this.optionD = optionD;
            this.correctAnswer = correctAnswer;
            this.explanation = explanation;
        }
    }
}
