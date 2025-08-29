import java.io.*;
import java.net.*;

public class RPSClient {
    private static final String SERVER = "localhost";
    private static final int PORT = 12345;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER, PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {

            // Thread đọc server
            Thread reader = new Thread(() -> {
                try {
                    String serverMsg;
                    while ((serverMsg = in.readLine()) != null) {
                        System.out.println("Server: " + serverMsg);
                    }
                } catch (IOException e) {
                    System.out.println("Mất kết nối tới server.");
                }
            });
            reader.start();

            // Thread nhập từ console
            while (true) {
                String input = console.readLine();
                if (input == null) break;
                out.println(input);
            }

        } catch (IOException e) {
            System.out.println("Không kết nối được tới server.");
        }
    }
}
