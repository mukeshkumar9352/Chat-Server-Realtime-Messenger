
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.logging.*;

public class SimpleChatClient extends JFrame {
    Socket socket;
    DataOutputStream dataOut;
    DataInputStream dataIn;

    ImageBackgroundPanel messagePanel;
    JScrollPane scrollPane;
    JTextField inputField;
    JButton sendButton, attachButton;
    JComboBox<String> userComboBox;
    boolean isBroadcast = true;
    String name;

    private static final Logger logger = Logger.getLogger(SimpleChatClient.class.getName());

    public SimpleChatClient(String name) {
        this.name = name;
        setupLogger();
        setupGUI();
        connectToServer();
        startReading();
    }

    void setupLogger() {
        try {
            FileHandler fh = new FileHandler("client_" + name + ".txt", true);
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
            logger.setUseParentHandlers(false);
        } catch (IOException e) {
            System.out.println("Logger failed: " + e.getMessage());
        }
    }

    void setupGUI() {
        setTitle("Chat - " + name);
        setSize(500, 600);
        setResizable(false);
        setLayout(new BorderLayout());

        messagePanel = new ImageBackgroundPanel("C:\\Users\\mukes\\OneDrive\\Desktop\\97c00759d90d786d9b6096d274ad3e07.jpg");
        scrollPane = new JScrollPane(messagePanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);

        inputField = new JTextField();
        inputField.setFont(new Font("Arial", Font.PLAIN, 16));

        sendButton = new JButton("Send");
        attachButton = new JButton("Attach");

        userComboBox = new JComboBox<>();
        userComboBox.addItem("Broadcast to All");
        userComboBox.setSelectedIndex(0);
        userComboBox.addActionListener(e -> isBroadcast = userComboBox.getSelectedIndex() == 0);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(userComboBox);
        buttonPanel.add(attachButton);
        buttonPanel.add(sendButton);

        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(e -> sendTypedMessage());
        attachButton.addActionListener(e -> sendFile());
        inputField.addActionListener(e -> sendButton.doClick());

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);
    }

    void sendTypedMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;

        String selectedUser = (String) userComboBox.getSelectedItem();
        if (!isBroadcast && selectedUser != null && !selectedUser.equals("Broadcast to All")) {
            sendPrivateMessage(selectedUser, text);
        } else {
            sendMessage(text);
        }
        addMessageBubble(text, true);
        inputField.setText("");
    }

    void addMessageBubble(String message, boolean isSent) {
        JPanel bubbleWrapper = new JPanel(new FlowLayout(isSent ? FlowLayout.RIGHT : FlowLayout.LEFT));
        bubbleWrapper.setOpaque(false);//using custom background

        JTextArea bubbleText = new JTextArea(message);
        bubbleText.setLineWrap(true);
        bubbleText.setWrapStyleWord(true);
        bubbleText.setEditable(false);
        bubbleText.setFont(new Font("Arial", Font.PLAIN, 14));
        bubbleText.setBackground(isSent ? new Color(220, 248, 198, 230) : new Color(240, 240, 240, 200));
        bubbleText.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        bubbleText.setOpaque(true);

        bubbleWrapper.add(bubbleText);
        messagePanel.add(bubbleWrapper);
        messagePanel.revalidate();
        messagePanel.repaint();

        SwingUtilities.invokeLater(() -> scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getMaximum()));
    }

    void connectToServer() {
        try {
            socket = new Socket("localhost", 7500);
            dataOut = new DataOutputStream(socket.getOutputStream());
            dataIn = new DataInputStream(socket.getInputStream());
            dataOut.writeUTF(name);
            dataOut.flush();
            addMessageBubble("Connected", false);
        } catch (IOException e) {
            addMessageBubble("Connection failed: ", false);
        }
    }

    void startReading() {
        new Thread(() -> {
            try {
                while (true) {
                    String type = dataIn.readUTF();
                    if (type.equals("File")) {
                        String filename = dataIn.readUTF();
                        int size = dataIn.readInt();
                        byte[] fileBytes = new byte[size];
                        dataIn.readFully(fileBytes);
                        FileOutputStream fos = new FileOutputStream("received_" + filename);
                        fos.write(fileBytes);
                        fos.close();
                        addMessageBubble("Received file: " + filename, false);
                        logger.info("Received file: " + filename);
                    } else if (type.startsWith("USER_LIST")) {
                        updateUserList(type);
                    } else {
                        addMessageBubble(type, false);
                        logger.info("Message received: " + type);
                    }
                }
            } catch (IOException e) {
                addMessageBubble("Disconnected.", false);
                logger.warning("Disconnected: " + e.getMessage());
            }
        }).start();
    }

    void updateUserList(String message) {
        SwingUtilities.invokeLater(() -> {
            userComboBox.removeAllItems();
            userComboBox.addItem("Broadcast to All");
            String[] parts = message.split("/");
            for (int i = 1; i < parts.length; i++) {
                if (!parts[i].equals(name)) {
                    userComboBox.addItem(parts[i]);
                }
            }
        });
    }

    void sendMessage(String msg) {
        try {
            dataOut.writeUTF(msg);
            dataOut.flush();
            logger.info("Sent message: " + msg);
        } catch (IOException e) {
            addMessageBubble("Error sending message.", false);
            logger.warning("Error sending message: " + e.getMessage());
        }
    }

    void sendPrivateMessage(String receiver, String msg) {
        try {
            dataOut.writeUTF("PRIVATE");
            dataOut.writeUTF(receiver);
            dataOut.writeUTF(msg);
            dataOut.flush();
            logger.info("Sent private message to " + receiver + ": " + msg);
        } catch (IOException e) {
            addMessageBubble("Error sending private message.", false);
            logger.warning("Error sending private message: " + e.getMessage());
        }
    }

    void sendFile() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                FileInputStream fis = new FileInputStream(file);
                byte[] buffer = new byte[(int) file.length()];
                fis.read(buffer);
                fis.close();

                String selectedUser = (String) userComboBox.getSelectedItem();
                if (!isBroadcast && selectedUser != null && !selectedUser.equals("Broadcast to All")) {
                    dataOut.writeUTF("PRIVATE_FILE");
                    dataOut.writeUTF(selectedUser);
                } else {
                    dataOut.writeUTF("File");
                }

                dataOut.writeUTF(file.getName());
                dataOut.writeInt(buffer.length);
                dataOut.write(buffer);
                dataOut.flush();

                addMessageBubble("File sent: " + file.getName(), true);
                logger.info("File sent: " + file.getName());
            } catch (IOException e) {
                addMessageBubble("Error sending file.", false);
                logger.warning("Error sending file: " + e.getMessage());
            }
        }
    }
}

class ImageBackgroundPanel extends JPanel {
    private final Image background;

    public ImageBackgroundPanel(String imagePath) {
        this.background = new ImageIcon(imagePath).getImage();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(background, 0, 0, getWidth(), getHeight(), this);
    }
}
