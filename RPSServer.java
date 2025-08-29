import java.io.*;
import java.net.*;
import java.util.*;

public class RPSServer {
    private static final int PORT = 12345;
    private static final List<ClientHandler> waitingList = new ArrayList<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server đang chạy ở cổng " + PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Người chơi mới kết nối: " + socket);

                ClientHandler clientHandler = new ClientHandler(socket);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ===================== Client Handler =====================
    static class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void sendMessage(String msg) {
            out.println(msg);
        }

        public String readMessage() throws IOException {
            return in.readLine();
        }

        @Override
        public void run() {
            try {
                sendMessage("Chào mừng! Bạn muốn chơi với (1) Người hay (2) Bot?");
                String choice = readMessage();

                if ("1".equals(choice)) { // PVP
                    synchronized (waitingList) {
                        if (waitingList.isEmpty()) {
                            sendMessage("Đang chờ người chơi khác...");
                            waitingList.add(this);
                        } else {
                            ClientHandler other = waitingList.remove(0);
                            GameSession session = new GameSession(this, other);
                            new Thread(session).start();
                        }
                    }
                } else { // vs Bot
                    GameSession session = new GameSession(this, null);
                    new Thread(session).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // ===================== Game Session =====================
    static class GameSession implements Runnable {
        private ClientHandler player1, player2;
        private boolean vsBot;

        public GameSession(ClientHandler p1, ClientHandler p2) {
            this.player1 = p1;
            this.player2 = p2;
            this.vsBot = (p2 == null);
        }

        @Override
        public void run() {
            try {
                int score1 = 0, score2 = 0;

                while (score1 < 2 && score2 < 2) {
                    player1.sendMessage("Nhập lựa chọn (rock, paper, scissors): ");
                    String move1 = player1.readMessage();

                    String move2;
                    if (vsBot) {
                        move2 = randomMove();
                        player1.sendMessage("Bot chọn: " + move2);
                    } else {
                        player2.sendMessage("Nhập lựa chọn (rock, paper, scissors): ");
                        move2 = player2.readMessage();
                    }

                    int result = determineWinner(move1, move2);
                    if (result == 1) {
                        score1++;
                        if (vsBot) player1.sendMessage("Bạn thắng ván này!");
                        else {
                            player1.sendMessage("Bạn thắng ván này!");
                            player2.sendMessage("Bạn thua ván này!");
                        }
                    } else if (result == -1) {
                        score2++;
                        if (vsBot) player1.sendMessage("Bot thắng ván này!");
                        else {
                            player1.sendMessage("Bạn thua ván này!");
                            player2.sendMessage("Bạn thắng ván này!");
                        }
                    } else {
                        if (vsBot) player1.sendMessage("Hòa ván này!");
                        else {
                            player1.sendMessage("Hòa ván này!");
                            player2.sendMessage("Hòa ván này!");
                        }
                    }

                    if (vsBot) {
                        player1.sendMessage("Tỷ số: Bạn " + score1 + " - " + score2 + " Bot");
                    } else {
                        player1.sendMessage("Tỷ số: Bạn " + score1 + " - " + score2 + " Đối thủ");
                        player2.sendMessage("Tỷ số: Bạn " + score2 + " - " + score1 + " Đối thủ");
                    }
                }

                if (vsBot) {
                    if (score1 > score2) player1.sendMessage("Bạn thắng trận Best of 3!");
                    else player1.sendMessage("😢 Bot thắng trận Best of 3!");
                } else {
                    if (score1 > score2) {
                        player1.sendMessage("Bạn thắng trận Best of 3!");
                        player2.sendMessage("Bạn thua trận Best of 3!");
                    } else {
                        player1.sendMessage("Bạn thua trận Best of 3!");
                        player2.sendMessage("Bạn thắng trận Best of 3!");
                    }
                }

                player1.sendMessage("Bạn có muốn chơi tiếp không? (yes/no)");
                if (vsBot) {
                    String ans = player1.readMessage();
                    if ("yes".equalsIgnoreCase(ans)) {
                        new Thread(new GameSession(player1, null)).start();
                    } else {
                        player1.sendMessage("Cảm ơn đã chơi!");
                    }
                } else {
                    player2.sendMessage("Bạn có muốn chơi tiếp không? (yes/no)");
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private String randomMove() {
            String[] moves = {"rock", "paper", "scissors"};
            return moves[new Random().nextInt(moves.length)];
        }

        private int determineWinner(String m1, String m2) {
            if (m1.equals(m2)) return 0;
            if ((m1.equals("rock") && m2.equals("scissors")) ||
                (m1.equals("scissors") && m2.equals("paper")) ||
                (m1.equals("paper") && m2.equals("rock"))) return 1;
            return -1;
        }
    }
}
