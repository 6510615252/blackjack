import java.io.*;
import java.net.*;
import javax.swing.*;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.Style;
import java.awt.*;

public class Client extends JFrame {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String serverAddress;
    private int serverPort;

    private JTextPane messageArea;
    private JTextField inputField;
    private JButton hitButton;
    private JButton standButton;
    private JPanel cardPanel;
    private JLabel dealerCardLabel;
    private boolean isMyTurn = false;
    

    public Client(String serverAddress, int port) {
        this.serverAddress = serverAddress;
        this.serverPort = port;
        setupGUI();
        connectToServer();
    }

    private void connectToServer() {
        try {
            Socket initialSocket = new Socket(serverAddress, serverPort);
            BufferedReader initialIn = new BufferedReader(new InputStreamReader(initialSocket.getInputStream()));
            PrintWriter initialOut = new PrintWriter(initialSocket.getOutputStream(), true);

            String response = initialIn.readLine();
            if (response != null && response.startsWith("NEW_PORT ")) {
                int newPort = Integer.parseInt(response.substring("NEW_PORT ".length()));
                showMessage("Server requested new connection on port: " + newPort);
                initialIn.close();
                initialOut.close();
                initialSocket.close();
                establishMainConnection(newPort);
            } else if (response != null && response.equals("SERVER_FULL")) {
                showMessage("Server is full. Cannot connect.");
                hitButton.setEnabled(false);
                standButton.setEnabled(false);
            } else {
                showMessage("Unexpected response from server: " + response);
                hitButton.setEnabled(false);
                standButton.setEnabled(false);
            }

        } catch (IOException e) {
            showMessage("Can't connect to initial server port: " + e.getMessage());
            hitButton.setEnabled(false);
            standButton.setEnabled(false);
        }
    }

    private void establishMainConnection(int port) {
        try {
            socket = new Socket(serverAddress, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            new Thread(this::receiveMessages).start();

            String name = JOptionPane.showInputDialog("Enter your name:");
            sendMessage(name);

        } catch (IOException e) {
            showMessage("Can't connect to server on port " + port + ": " + e.getMessage());
            hitButton.setEnabled(false);
            standButton.setEnabled(false);
        }
    }

    private void setupGUI() {
        setTitle("Blackjack Client");
        setSize(600, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        messageArea = new JTextPane();
        messageArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(messageArea);

        inputField = new JTextField();
        inputField.addActionListener(e -> {
            sendMessage(inputField.getText());
            inputField.setText("");
        });

        JPanel controlPanel = new JPanel();
        hitButton = new JButton("HIT");
        standButton = new JButton("STAND");
        hitButton.setEnabled(false);
        standButton.setEnabled(false);

        hitButton.addActionListener(e -> sendMessage("HIT"));
        standButton.addActionListener(e -> sendMessage("STAND"));

        controlPanel.add(hitButton);
        controlPanel.add(standButton);

        cardPanel = new JPanel();
        cardPanel.setLayout(new BoxLayout(cardPanel, BoxLayout.Y_AXIS));

        JScrollPane cardScrollPane = new JScrollPane(cardPanel);
        cardScrollPane.setPreferredSize(new Dimension(150, 0));
        cardScrollPane.setMinimumSize(new Dimension(100, 0));

        dealerCardLabel = new JLabel("Dealer: ?");

        add(scrollPane, BorderLayout.CENTER);
        add(inputField, BorderLayout.NORTH);
        add(controlPanel, BorderLayout.SOUTH);
        add(cardScrollPane, BorderLayout.EAST);
        add(dealerCardLabel, BorderLayout.WEST);

        setVisible(true);
    }

    private void receiveMessages() {
        try {
            String line;
            hitButton.setEnabled(false);
            standButton.setEnabled(false);
            while ((line = in.readLine()) != null) {
                if (line.equals("INPUT_NAME")) {
                    continue;
                } else if (line.equals("GAME_START")) {
                    showMessage("Game started!");
                    cardPanel.removeAll();
                    cardPanel.revalidate();
                    cardPanel.repaint();
                    dealerCardLabel.setText("Dealer: ?");
                } else if (line.startsWith("INITIAL_CARDS")) {
                    String[] cards = line.substring("INITIAL_CARDS ".length()).split(" ");
                    cardPanel.removeAll();
                    for (String cardStr : cards) {
                        JLabel cardLabel = new JLabel(cardStr);
                        cardPanel.add(cardLabel);
                    }
                    cardPanel.revalidate();
                    cardPanel.repaint();
                } else if (line.startsWith("NEW_CARD")) {
                    String cardStr = line.substring("NEW_CARD ".length());
                    JLabel cardLabel = new JLabel(cardStr);
                    cardPanel.add(cardLabel);
                    cardPanel.revalidate();
                    cardPanel.repaint();
                } else if (line.startsWith("DEALER_FIRST_CARD")) {
                    String cardStr = line.substring("DEALER_FIRST_CARD ".length());
                    dealerCardLabel.setText("Dealer: " + cardStr);
                } else if (line.startsWith("DEALER_HAND")) {
                    showMessageWithDelay("Dealer's hand: " + line.substring("DEALER_HAND ".length()), 1000);
                } else if (line.startsWith("DEALER_HIT")) {
                    showMessageWithDelay("Dealer hits: " + line.substring("DEALER_HIT ".length()), 1000);
                } else if (line.startsWith("YOUR_TURN")) {
                    showMessage("It's your turn.");
                    isMyTurn = true;
                    updateControlButtons();
                } else {
                    showMessage(line);
                    isMyTurn = false;
                    updateControlButtons();
                }
            }
        } catch (IOException e) {
            showMessage("Connection to server lost.");
            isMyTurn = false;
            updateControlButtons();
        } finally {
            try {
                if (socket != null)
                    socket.close();
                if (in != null)
                    in.close();
                if (out != null)
                    out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    private void showMessageWithDelay(String message, int delay) {
        SwingUtilities.invokeLater(() -> {
            try {
                Thread.sleep(delay); 
                showMessage(message);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    private void showMessage(String message) {
         SwingUtilities.invokeLater(() -> {
            try {
                StyledDocument doc = messageArea.getStyledDocument();
                Style style = messageArea.addStyle("myStyle", null);

                if (message.startsWith("Game started!")) {
                StyleConstants.setForeground(style, new Color(0, 150, 0)); 
            } else if (message.startsWith("It's your turn.")) {
                StyleConstants.setForeground(style, new Color(0, 100, 200)); 
            } else if (message.startsWith("Not enough cards")) {
                StyleConstants.setForeground(style, Color.RED);            
            } else if (message.contains("BUSTED")) {
                StyleConstants.setForeground(style, Color.RED);            
            } else if (message.contains("LOSE")) {
                StyleConstants.setForeground(style, Color.RED);            
            } else if (message.contains("WIN")) {
                StyleConstants.setForeground(style, new Color(0, 150, 0)); 
            } else if (message.contains("PUSH")) {
                StyleConstants.setForeground(style, new Color(100, 100, 100));
            } else if (message.contains("STANDS")) {
                StyleConstants.setForeground(style, new Color(200, 100, 0)); 
            } else if (message.contains("HITS")) {
                StyleConstants.setForeground(style, new Color(200, 100, 0));     
            } else if (message.contains("DEALER_TURN")) {
                StyleConstants.setForeground(style, new Color(150, 0, 150)); 
            } else if (message.contains("Dealer hits:")) {
                StyleConstants.setForeground(style, new Color(150, 0, 150));  
            } else if (message.contains("CLEAR_HAND")) {
                StyleConstants.setForeground(style, Color.LIGHT_GRAY);    
            } else {
                StyleConstants.setForeground(style, Color.BLACK);      
            }

                doc.insertString(doc.getLength(), message + "\n", style);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    private void updateControlButtons() {
        hitButton.setEnabled(isMyTurn);
        standButton.setEnabled(isMyTurn);
    }

    private void sendMessage(String message) {
        if (out != null && !out.checkError()) {
            out.println(message);
            out.flush();
        }
    }

    public static void main(String[] args) {
        String serverAddress = (args.length == 0) ? "localhost" : args[0];
        int port = 10000; // Initial port to connect to
        SwingUtilities.invokeLater(() -> new Client(serverAddress, port));
    }
}

