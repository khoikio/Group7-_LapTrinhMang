import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class RPSServer {
    private static final int PORT = 12345;

    // Hàng đợi ghép cặp (bọc sync khi thao tác)
    private static final Queue<ClientHandler> waitingPlayers = new LinkedList<>();

    // Game id an toàn đa luồng
    private static final AtomicInteger GAME_ID = new AtomicInteger(0);
    private static int nextGameId() { return GAME_ID.incrementAndGet(); }

    public static void main(String[] args) {
        System.out.println("-.- Server khoi dong o cong " + PORT + " roi nha may");
        try {
            ServerSocket serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true); // cho phép tái sử dụng cổng khi restart nhanh
            serverSocket.bind(new InetSocketAddress(PORT));

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("-> Mot nguoi choi moi da ket noi: " + clientSocket.getInetAddress());
                new Thread(new ClientHandler(clientSocket), "ClientHandler-" + clientSocket.getPort()).start();
            }
        } catch (IOException e) {
            System.out.println("Loi server: " + e.getMessage());
        }
    }

    // ================= Session quản lý 1 trận giữa 2 người =================
    static class GameSession {
        final ClientHandler p1, p2;
        final CyclicBarrier barrier = new CyclicBarrier(2); // đồng bộ mỗi vòng
        volatile String p1Move, p2Move;
        volatile boolean playing = true;
        final int gameId;
        int p1Score = 0, p2Score = 0;

        GameSession(ClientHandler p1, ClientHandler p2, int gameId) {
            this.p1 = p1;
            this.p2 = p2;
            this.gameId = gameId;
        }

        void setMove(ClientHandler who, String move) {
            if (who == p1) p1Move = move;
            else p2Move = move;
        }

        void clearMoves() { p1Move = null; p2Move = null; }
    }

    // ================== ClientHandler: 1 người chơi ==================
    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;

        private String name = "Anonymous";
        private volatile boolean connected = true;

        private ClientHandler opponent;
        private GameSession session;
        private boolean isHost = false; // chỉ host tính & broadcast kết quả
        private boolean playingBot = false;

        ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8"), true);
                in  = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"));

                send("Nhap ten may di: ");
                String n = readLineOrNull();
                name = (n == null || n.isBlank()) ? name : n.trim();
                send("Chao may, " + name + "! Chao mung den voi game Keo-Bua-Bao.");

                mainMenuLoop();

            } catch (IOException e) {
                System.out.println("Loi ket noi tu client " + name + ": " + e.getMessage());
            } finally {
                cleanup();
                System.out.println("-> Nguoi choi " + name + " da thoat.");
            }
        }

        // ========================= Vòng menu chính =========================
        private void mainMenuLoop() throws IOException {
            while (connected) {
                detachFromOpponentAndSession();
                playingBot = false;

                send("\nChon che do choi:\n" +
                     "1. Choi voi may (bot)\n" +
                     "2. Choi voi nguoi (matchmaking)\n" +
                     "Nhap lua chon (1/2), hoac q de thoat: ");

                String choice = readLineOrNull();
                if (choice == null) break;
                choice = choice.trim().toLowerCase();

                if (choice.equals("q")) {
                    send("Thoat game. Bye nhe!");
                    break;
                } else if (choice.equals("1")) {
                    playingBot = true;
                    playVsBot();
                } else if (choice.equals("2")) {
                    tryMatchmaking();
                } else {
                    send("Lua chon khong hop le, nhap lai di may.");
                }
            }
        }

        // ======================= Matchmaking & PVP ========================
        private void tryMatchmaking() throws IOException {
            send("Dang tim doi thu...");

            synchronized (waitingPlayers) {
                if (waitingPlayers.isEmpty()) {
                    waitingPlayers.add(this);
                    send("Chua co ai cho, may doi chut nhe -.-");
                } else {
                    ClientHandler w = waitingPlayers.poll();
                    if (w != null && w.connected) {
                        pairAndStart(w, this); // w là host (p1), this là p2
                        // p2 sẽ tiếp tục phía dưới, p1 cũng tự chơi trong thread của nó
                    } else {
                        waitingPlayers.add(this);
                        send("Chua co ai cho, may doi chut nhe -.-");
                    }
                }
            }

            // Đợi đến khi bị ghép (nếu chưa)
            if (opponent == null) {
                synchronized (this) {
                    while (connected && opponent == null) {
                        try { this.wait(); } catch (InterruptedException ignored) {}
                    }
                }
            }

            if (!connected || opponent == null || session == null) {
                send("Khong the bat dau tran (doi thu roi). Quay ve menu.");
                return;
            }

            playHumanVsHuman();
        }

        private static void pairAndStart(ClientHandler p1, ClientHandler p2) {
            int id = nextGameId();
            GameSession s = new GameSession(p1, p2, id);

            p1.session = s; p2.session = s;
            p1.opponent = p2; p2.opponent = p1;
            p1.isHost = true;  p2.isHost = false;

            p1.send("\n--- Tim thay doi thu: " + p2.name + ". Bat dau Game #" + id + " ---");
            p2.send("\n--- Tim thay doi thu: " + p1.name + ". Bat dau Game #" + id + " ---");
            System.out.println("--- Game " + id + ": " + p1.name + " vs " + p2.name + " da bat dau! ---");

            // >>> FIX: đánh thức thằng đang đợi (p1), và cũng notify p2 cho an toàn
            synchronized (p1) { p1.notify(); }
            synchronized (p2) { p2.notify(); }
        }

        private void playHumanVsHuman() throws IOException {
            GameSession s = this.session;
            if (s == null) { send("Loi session. Quay ve menu."); return; }

            while (connected && s.playing && s.p1Score < 3 && s.p2Score < 3) {
                String myMove = getPlayerMove();
                if (myMove == null) { s.playing = false; break; }
                s.setMove(this, myMove);

                if (!awaitBarrier(s)) break; // chờ cả 2 nhập

                if (isHost) {
                    String result = getResult(s.p1Move, s.p2Move);
                    if ("player1".equals(result)) s.p1Score++;
                    else if ("player2".equals(result)) s.p2Score++;

                    sendRoundResult(s.p1, s.p2, s, result);
                    sendRoundResult(s.p2, s.p1, s, invert(result));
                    s.clearMoves();
                }

                if (!awaitBarrier(s)) break; // chờ host broadcast xong
            }

            if (s.playing && isHost) {
                announceWinner(s);
            }

            detachFromOpponentAndSession();
            send("Tran ket thuc. Quay ve menu chinh.\n");
        }

        private boolean awaitBarrier(GameSession s) {
            try {
                s.barrier.await();
                return true;
            } catch (InterruptedException | BrokenBarrierException e) {
                s.playing = false;
                return false;
            }
        }

        private void announceWinner(GameSession s) {
            String winnerName = (s.p1Score == 3) ? s.p1.name : s.p2.name;
            String loserName  = (s.p1Score == 3) ? s.p2.name : s.p1.name;

            s.p1.send(s.p1Score == 3 ? "--- Chuc mung, may thang tran nay! :)) ---"
                                     : "--- Tiec qua, may da thua roi. ---");
            s.p2.send(s.p2Score == 3 ? "--- Chuc mung, may thang tran nay! :)) ---"
                                     : "--- Tiec qua, may da thua roi. ---");

            System.out.println("-> Game " + s.gameId + " winner: " + winnerName + " (thua: " + loserName + ")");
        }

        private static String invert(String r) {
            if ("player1".equals(r)) return "player2";
            if ("player2".equals(r)) return "player1";
            return "hoa";
        }

        private void sendRoundResult(ClientHandler me, ClientHandler opp, GameSession s, String resultFromP1View) {
            boolean iAmP1 = (me == s.p1);
            String myMove = iAmP1 ? s.p1Move : s.p2Move;
            String oppMove = iAmP1 ? s.p2Move : s.p1Move;

            String outcome;
            if ("hoa".equals(resultFromP1View)) outcome = "Hoa roi, ca hai khong duoc diem nao.";
            else if ((iAmP1 && "player1".equals(resultFromP1View)) || (!iAmP1 && "player2".equals(resultFromP1View)))
                outcome = "May thang van nay! kkk.";
            else
                outcome = "May thua van nay, " + opp.name + " thang.";

            me.send("--- Ket qua van nay: may chon " + myMove + ", " + opp.name + " chon " + oppMove + " ---");
            me.send("Ket qua: " + outcome);
            me.send("Diem: " + s.p1Score + " - " + s.p2Score + "\n");
        }

        // =========================== Bot mode ===========================
        private void playVsBot() throws IOException {
            int myScore = 0, botScore = 0;

            send("--- Bat dau choi voi Bot ---");
            while (connected && myScore < 3 && botScore < 3) {
                String pMove = getPlayerMove();
                if (pMove == null) break;
                String bMove = getBotMove();
                String res = getResult(pMove, bMove);
                if ("player1".equals(res)) myScore++;
                else if ("player2".equals(res)) botScore++;

                send("--- May: " + pMove + " | Bot: " + bMove + " ---");
                send("Diem: " + myScore + " - " + botScore + "\n");
            }

            if (!connected) return;
            send(myScore == 3 ? "--- Chuc mung, may thang bot! ---"
                              : "--- Thua bot roi nhe :)) ---");

            send("Ban muon choi lai voi bot khong? (y/n): ");
            String again = readLineOrNull();
            if (again != null && again.trim().equalsIgnoreCase("y")) {
                playVsBot();
            } else {
                send("Quay ve menu chinh.\n");
            }
        }

        // =========================== Utils I/O ===========================
        private void send(String msg) {
            if (out != null) out.println(msg);
        }

        private String readLineOrNull() throws IOException {
            String line = (in != null) ? in.readLine() : null;
            if (line == null) connected = false;
            return line;
        }

        private String getPlayerMove() throws IOException {
            while (connected) {
                send("Chon (keo, bua, bao) va enter: ");
                String move = readLineOrNull();
                if (!connected || move == null) return null;
                move = move.trim().toLowerCase();
                if (move.equals("keo") || move.equals("bua") || move.equals("bao")) return move;
                send("Lua chon khong hop le, nhap lai di may.");
            }
            return null;
            }

        // =========================== Cleanup ===========================
        private void detachFromOpponentAndSession() {
            synchronized (waitingPlayers) {
                waitingPlayers.remove(this);
            }
            if (session != null) {
                session.playing = false;
                if (opponent != null) {
                    opponent.send("Doi thu " + name + " da roi tran / roi game.");
                    synchronized (opponent) { opponent.notify(); }
                }
            }
            opponent = null;
            session = null;
            isHost = false;
        }

        private void cleanup() {
            connected = false;
            synchronized (waitingPlayers) {
                waitingPlayers.remove(this);
            }
            if (session != null) {
                session.playing = false;
                if (opponent != null) {
                    synchronized (opponent) { opponent.notify(); }
                }
            }
            try { if (in != null) in.close(); } catch (IOException ignored) {}
            if (out != null) out.close();
            try { if (clientSocket != null) clientSocket.close(); } catch (IOException ignored) {}
        }
    }

    // ====================== Logic Keo-Bua-Bao ======================
    private static String getBotMove() {
        String[] options = {"keo", "bua", "bao"};
        return options[ThreadLocalRandom.current().nextInt(options.length)];
    }

    // Trả về "hoa", "player1", "player2" — player1 là p1 trong session
    private static String getResult(String move1, String move2) {
        if (move1.equals(move2)) return "hoa";
        if ((move1.equals("keo") && move2.equals("bao")) ||
            (move1.equals("bua") && move2.equals("keo")) ||
            (move1.equals("bao") && move2.equals("bua"))) {
            return "player1";
        }
        return "player2";
    }
}
