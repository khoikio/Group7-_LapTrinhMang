import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RPSClient - Client console cho RPSServer
 * Cách dùng:
 *   javac RPSClient.java
 *   java RPSClient                 // mặc định localhost:12345
 *   java RPSClient 127.0.0.1 12345 // host, port tùy ý
 *
 * Tính năng:
 * - Thread riêng để đọc message từ server và in ra console theo thời gian thực.
 * - Thread chính đọc input từ người chơi và gửi sang server (dòng nào Enter là gửi).
 * - UTF-8 đầy đủ.
 * - Đóng kết nối gọn khi server tắt hoặc user bấm Ctrl+D/Ctrl+Z (EOF).
 */
public class RPSClient {
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = 12345;
        if (args.length > 1) {
            try { port = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
        }

        System.out.println("Dang ket noi toi " + host + ":" + port + " ...");
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000); // 5s timeout
            socket.setTcpNoDelay(true);

            try (
                BufferedReader serverIn = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter serverOut = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                BufferedReader userIn = new BufferedReader(
                        new InputStreamReader(System.in, StandardCharsets.UTF_8))
            ) {
                System.out.println("Da ket noi! Goi y: nhap ten khi server hoi, sau do chon 1 (bot) hoac 2 (match).");
                AtomicBoolean running = new AtomicBoolean(true);

                // Thread đọc từ server
                Thread reader = new Thread(() -> {
                    try {
                        String line;
                        while ((line = serverIn.readLine()) != null) {
                            System.out.println(line);
                        }
                        System.out.println("[Server da dong ket noi]");
                    } catch (IOException e) {
                        System.out.println("[Mat ket noi toi server: " + e.getMessage() + "]");
                    } finally {
                        running.set(false);
                        // Đánh dấu EOF cho stdin để main loop thoát, hoặc cứ để running false là đủ
                    }
                }, "ServerReader");
                reader.setDaemon(true);
                reader.start();

                // Thread chính: đọc user input và gửi đi
                String input;
                while (running.get() && (input = userIn.readLine()) != null) {
                    serverOut.println(input);
                    // PrintWriter auto-flush true
                }

                // Nếu userIn EOF hoặc running=false, đóng lại
                running.set(false);
                try { reader.join(1000); } catch (InterruptedException ignored) {}

            }
        } catch (ConnectException ce) {
            System.out.println("Khong ket noi duoc: " + ce.getMessage());
        } catch (SocketTimeoutException te) {
            System.out.println("Time out khi ket noi: " + te.getMessage());
        } catch (IOException e) {
            System.out.println("Loi I/O: " + e.getMessage());
        }

        System.out.println("Client thoat.");
    }
}
