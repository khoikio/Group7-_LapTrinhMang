import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.Socket;

public class ClientGUI extends JFrame {
    private JTextArea textArea;
    private JTextField inputField;
    private JButton sendButton;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public ClientGUI(String host, int port) {
        setTitle("Rock Paper Scissors Client");
        setSize(500, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // UI chính
        textArea = new JTextArea();
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);

        inputField = new JTextField();
        sendButton = new JButton("Gửi");

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);

        add(scrollPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // Action: khi bấm nút hoặc nhấn Enter
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

        inputField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

        // Kết nối tới server
        try {
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            // Thread lắng nghe từ server
            new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        appendMessage("Server: " + line);
                    }
                } catch (IOException ex) {
                    appendMessage("Mất kết nối với server.");
                }
            }).start();

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Không thể kết nối tới server: " + ex.getMessage());
        }
    }

    private void sendMessage() {
        String msg = inputField.getText().trim();
        if (!msg.isEmpty() && out != null) {
            out.println(msg);
            appendMessage("Bạn: " + msg);
            inputField.setText("");
        }
    }

    private void appendMessage(String msg) {
        SwingUtilities.invokeLater(() -> {
            textArea.append(msg + "\n");
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ClientGUI client = new ClientGUI("127.0.0.1", 12345); // đổi IP/port nếu cần
            client.setVisible(true);
        });
    }
}
