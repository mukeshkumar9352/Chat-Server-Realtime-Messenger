import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class BroadcastServer extends JFrame {
    ServerSocket serverSocket;
    static final int PORT = 7500;
    final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    DefaultListModel<String> clientListModel;
    JTextArea logArea;

    public BroadcastServer() {
        setupGUI();
        new Thread(()->startServer()).start();
    }

    private void setupGUI() {
        setTitle("Broadcast Server");
        setSize(600, 500);
        setLayout(new BorderLayout());
        setResizable(false);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        clientListModel = new DefaultListModel<>();
        JList<String> clientList = new JList<>(clientListModel);
        JScrollPane clientScroll = new JScrollPane(clientList);
        clientScroll.setBorder(BorderFactory.createTitledBorder("Active Clients"));
        clientScroll.setPreferredSize(new Dimension(200, 0));
        add(clientScroll, BorderLayout.WEST);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Server Log"));
        add(logScroll, BorderLayout.CENTER);

        setVisible(true);
    }

    private void startServer() {
        try {
            serverSocket = new ServerSocket(PORT);
            appendLog("Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                DataInputStream tempIn = new DataInputStream(clientSocket.getInputStream());
                String clientName = tempIn.readUTF();

                appendLog("Client connected: " + clientName);
                ClientHandler handler = new ClientHandler(clientSocket, clientName);
                clients.put(clientName, handler);

                SwingUtilities.invokeLater(() -> {
                    clientListModel.addElement(clientName);
                    broadcastClientList();
                });

                new Thread(handler).start();
            }
        } catch (IOException e) {
            appendLog("Error starting server: " + e.getMessage());
        }
    }

    private void broadcastClientList() {
        StringBuilder sb = new StringBuilder("USER_LIST");
        for (String name : clients.keySet()) {
            sb.append("/").append(name);
        }

        for (ClientHandler c : clients.values()) {
            try {
                c.sendText(sb.toString());
            } catch (IOException e) {
                appendLog("Failed to send user list to " + c.clientName);
            }
        }
    }

    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    class ClientHandler implements Runnable {
        private final Socket socket;
        private final String clientName;
        private DataInputStream in;
        private DataOutputStream out;

        public ClientHandler(Socket socket, String clientName) {
            this.socket = socket;
            this.clientName = clientName;
            try {
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());
            } catch (IOException e) {
                appendLog("Error setting up streams for " + clientName);
            }
        }

        public void run() {
            try {
                while (true) {
                    String type = in.readUTF();

                    if (type.equals("File")) {
                        String fileName = in.readUTF();
                        int size = in.readInt();
                        byte[] fileData = new byte[size];
                        in.readFully(fileData);

                        appendLog(clientName + " sent file: " + fileName);
                        broadcastFile(fileName, fileData, this);
                    } else if (type.equals("PRIVATE_FILE")) {
                        String receiver = in.readUTF();
                        String fileName = in.readUTF();
                        int size = in.readInt();
                        byte[] fileData = new byte[size];
                        in.readFully(fileData);

                        ClientHandler target = clients.get(receiver);
                        if (target != null) {
                            appendLog("[Private File] " + clientName + " -> " + receiver + ": " + fileName);
                            target.sendFile(fileName, fileData);
                        } else {
                            sendText("User '" + receiver + "' not found.");
                        }
                    } else if (type.equals("PRIVATE")) {
                        String receiver = in.readUTF();
                        String message = in.readUTF();
                        ClientHandler target = clients.get(receiver);
                        if (target != null) {
                            appendLog("[Private] " + clientName + " -> " + receiver + ": " + message);
                            target.sendText("[Private] " + clientName + ": " + message);
                        }
                    } else {
                        appendLog(clientName + ": " + type);
                        broadcastMessage(clientName + ": " + type, this);
                    }
                }
            } catch (IOException e) {
                appendLog("Client disconnected: " + clientName);
            } finally {
                clients.remove(clientName);
                SwingUtilities.invokeLater(() -> {
                    clientListModel.removeElement(clientName);
                    broadcastClientList();
                });
                try {
                    socket.close();
                } catch (IOException e) {}
            }
        }

        public void sendText(String msg) throws IOException {
            out.writeUTF(msg);
            out.flush();
        }

        public void sendFile(String filename, byte[] data) throws IOException {
            out.writeUTF("File");
            out.writeUTF(filename);
            out.writeInt(data.length);
            out.write(data);
            out.flush();
        }
    }

    private void broadcastMessage(String message, ClientHandler sender) {
        for (ClientHandler client : clients.values()) {
            if (client != sender) {
                try {
                    client.sendText(message);
                } catch (IOException e) {
                    appendLog("Failed to send message to " + client.clientName);
                }
            }
        }
    }

    private void broadcastFile(String fileName, byte[] data, ClientHandler sender) {
        for (ClientHandler client : clients.values()) {
            if (client != sender) {
                try {
                    client.sendFile(fileName, data);
                } catch (IOException e) {
                    appendLog("Failed to send file to " + client.clientName);
                }
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(()->new BroadcastServer());
    }
}
