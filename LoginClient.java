import javax.swing.*;
import java.awt.*;
import java.sql.*;

public class LoginClient extends JFrame {
    JTextField usernameField;
    JPasswordField passwordField;
    JButton loginButton, registerButton;
    JLabel statusLabel;
    String url=System.getenv("DB_URL");
    String pass=System.getenv("DB_PASS");
    String user_sql=System.getenv("DB_USER");

    public LoginClient() {
        setTitle("Login Page");
        setSize(400, 300); 
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        setLayout(new GridLayout(6, 1, 10, 10)); 

        usernameField = new JTextField();
        passwordField = new JPasswordField();
        loginButton = new JButton("Login");
        registerButton = new JButton("Register");
        statusLabel = new JLabel("", SwingConstants.CENTER);

        add(new JLabel("Username:", SwingConstants.CENTER));
        add(usernameField);
        add(new JLabel("Password:", SwingConstants.CENTER));
        add(passwordField);
        add(loginButton);
        add(registerButton);
        add(statusLabel);

        loginButton.addActionListener(e -> {
            String user = usernameField.getText();
            String pass = new String(passwordField.getPassword());

            if (authenticate(user, pass)) {
                statusLabel.setText("Login Successful!");
                dispose(); // Close login window
                new SimpleChatClient(user); // Open chat client
            } else {
                statusLabel.setText("Invalid credentials.");
            }
        });

        // Register button action
        registerButton.addActionListener(e -> {
            String user = usernameField.getText();
            String pass = new String(passwordField.getPassword());

            if (user.isEmpty() || pass.isEmpty()) {
                statusLabel.setText("Please fill both fields.");
                return;
            }

            if (userExists(user)) {
                statusLabel.setText("Username already exists.");
            } else {
                if (registerUser(user, pass)) {
                    statusLabel.setText("User registered. You can login.");
                } else {
                    statusLabel.setText("Registration failed.");
                }
            }
        });

        setVisible(true);
    }

    private boolean userExists(String username) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(url,user_sql,pass);

            String query = "SELECT * FROM users WHERE username = ?";
            PreparedStatement pst = conn.prepareStatement(query);
            pst.setString(1, username);
            ResultSet rs = pst.executeQuery();

            boolean exists = rs.next();

            rs.close();
            pst.close();
            conn.close();

            return exists;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean authenticate(String username, String password) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(url,user_sql,pass);

            String query = "SELECT * FROM users WHERE username = ? AND password = ?";
            PreparedStatement pst = conn.prepareStatement(query);
            pst.setString(1, username);
            pst.setString(2, password);
            ResultSet rs = pst.executeQuery();

            boolean success = rs.next();

            rs.close();
            pst.close();
            conn.close();

            return success;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean registerUser(String username, String password) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(url,user_sql,pass);

            String query = "INSERT INTO users (username, password) VALUES (?, ?)";
            PreparedStatement pst = conn.prepareStatement(query);
            pst.setString(1, username);
            pst.setString(2, password);

            int rows = pst.executeUpdate();

            pst.close();
            conn.close();

            return rows > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    public static void main(String[] args) {
         SwingUtilities.invokeLater(()->new LoginClient());
    }
}
