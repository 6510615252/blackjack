import java.io.*;
import java.net.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.util.ArrayList;
import java.util.HashMap;

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
    private JLabel dealerCardLabel;
    private boolean isMyTurn = false;
    private JLabel[] playerCardLabels = new JLabel[11];

    private JPanel playerPanel;
    private JPanel dealerPanel;
    private JPanel controlPanel;
    private JPanel messagePanel;

    private ArrayList<JLabel> playerCards = new ArrayList<>();
    private ArrayList<JLabel> dealerCards = new ArrayList<>();
    private int cardWidth = 72;
    private int cardHeight = 96;
    private int cardSpacing = 20;

    private HashMap<String, ImageIcon> cardImages = new HashMap<>();
    private String imageFolderPath = "./images/";
    private boolean dealerHasFirstCard = false;

    public Client(String serverAddress, int port) {
        this.serverAddress = serverAddress;
        this.serverPort = port;
        setupGUI();
        connectToServer();
        getCardImage();
    }

    private void connectToServer() {
        try {
            Socket initialSocket = new Socket(serverAddress, serverPort);
            BufferedReader initialIn = new BufferedReader(new InputStreamReader(initialSocket.getInputStream()));
            PrintWriter initialOut = new PrintWriter(initialSocket.getOutputStream(), true);

            String response = initialIn.readLine();
            if (response.startsWith("NEW_PORT ")) {
                int newPort = Integer.parseInt(response.substring("NEW_PORT ".length()));
                showMessage("Server requested new connection on port: " + newPort);
                initialIn.close();
                initialOut.close();
                initialSocket.close();
                establishMainConnection(newPort);
            } else if (response.equals("SERVER_FULL")) {
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
        setSize(800, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 5);

        playerPanel = new JPanel();
        playerPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
        playerPanel.setLayout(new FlowLayout(FlowLayout.CENTER, cardSpacing, 10));
        playerPanel.setBorder(BorderFactory.createTitledBorder("Your Hand"));
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1;
        gbc.weighty = 0.2;
        add(playerPanel, gbc);

        dealerPanel = new JPanel();
        dealerPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
        dealerPanel.setLayout(new FlowLayout(FlowLayout.CENTER, cardSpacing, 10));
        dealerPanel.setBorder(BorderFactory.createTitledBorder("Dealer's Hand"));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.weighty = 0.2;
        add(dealerPanel, gbc);

        controlPanel = new JPanel();
        controlPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
        hitButton = new JButton("HIT");
        standButton = new JButton("STAND");
        hitButton.setEnabled(false);
        standButton.setEnabled(false);
        controlPanel.add(hitButton);
        controlPanel.add(standButton);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 1;
        gbc.weighty = 0.1;
        add(controlPanel, gbc);

        hitButton.addActionListener(e -> {
            if (isMyTurn) {
                sendMessage("HIT");
            } else {
                showMessage("NOT YOUR TURN");
            }
        });

        standButton.addActionListener(e -> {
            if (isMyTurn) {
                sendMessage("STAND");
            } else {
                showMessage("NOT YOUR TURN");
            }
        });

        messagePanel = new JPanel(new BorderLayout());
        messagePanel.setBorder(BorderFactory.createTitledBorder("Messages"));
        messageArea = new JTextPane();
        messageArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(messageArea);
        messagePanel.add(scrollPane, BorderLayout.CENTER);
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 1;
        gbc.weighty = 0.3;
        add(messagePanel, gbc);

        // inputField = new JTextField();
        // inputField.addActionListener(e -> {
        // sendMessage(inputField.getText().toLowerCase());
        // inputField.setText("");
        // });
        // gbc.gridx = 0;
        // gbc.gridy = 4;
        // gbc.weightx = 1;
        // gbc.weighty = 0.1;
        // add(inputField, gbc);

        dealerCardLabel = new JLabel("Dealer: ?");
        dealerPanel.add(dealerCardLabel);

        for (int i = 0; i < playerCardLabels.length; i++) {
            playerCardLabels[i] = new JLabel();
            playerPanel.add(playerCardLabels[i]);
        }

        setVisible(true);
    }

    private void receiveMessages() {
        try {
            String line;
            hitButton.setEnabled(false);
            standButton.setEnabled(false);
            while ((line = in.readLine()) != null) {
                final String message = line;
                SwingUtilities.invokeLater(() -> {
                    if (message.equals("GAME_START")) {
                        showMessage("START!");
                        isMyTurn = false;
                        updateControlButtons();
                        clearHands();
                        dealerHasFirstCard = false;
                        dealerCardLabel.setText("Dealer: ?");
                    } else if (message.startsWith("INITIAL_CARDS")) {
                        String[] cards = message.substring("INITIAL_CARDS ".length()).split(" ");
                        displayInitialCards(cards);
                    } else if (message.startsWith("NEW_CARD")) {
                        String cardStr = message.substring("NEW_CARD ".length());
                        addCardToPlayer(cardStr);
                    } else if (message.startsWith("DEALER_FIRST_CARD")) {
                        String cardStr = message.substring("DEALER_FIRST_CARD ".length());
                        dealerHasFirstCard = true;
                        JLabel cardLabel = getCardLabel(cardStr);
                        clearDealerHand();
                        dealerCards.add(cardLabel);
                        dealerPanel.add(cardLabel);
                        dealerPanel.revalidate();
                        dealerPanel.repaint();
                        dealerCardLabel.setText("Dealer: ");
                    } else if (message.startsWith("DEALER_HAND")) {
                        showMessage(message);
                        displayDealerHand(message);
                    } else if (message.startsWith("DEALER_HIT")) {
                        showMessage(message);
                        addCardToDealer(message.substring("DEALER_HIT ".length()));
                    } else if (message.equals("YOUR_TURN")) {
                        showMessage("It's your turn!");
                        isMyTurn = true;
                        updateControlButtons();
                    } else if (message.equals("CLEAR_HAND")) {
                        showMessage(message);
                        clearHands();
                    } else if (message.equals("NEW_ROUND_START")) {
                        showMessage("START A NEW ROUND!");
                        isMyTurn = false;
                        updateControlButtons();
                        clearHands();
                        dealerHasFirstCard = false;
                        dealerCardLabel.setText("Dealer: ?");
                    } else {
                        showMessage(message);
                        isMyTurn = false;
                        updateControlButtons();
                    }
                });
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
                } else if (message.contains("CLEAR_HAND") || message.contains("GAME_OVER:") || message.contains("REMAINING CARDS:") || message.contains("NEW DECK CREATED") || message.contains("Starting new round") || message.contains("DECK RAN  OUT OF CARD")) {
                    StyleConstants.setForeground(style, Color.WHITE);
                    StyleConstants.setBackground(style, Color.black);
                    StyleConstants.setFontSize(style, 16);
                    StyleConstants.setBold(style, true);
                } else {
                    StyleConstants.setForeground(style, Color.BLACK);
                }

                doc.insertString(doc.getLength(), message + "\n", style);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void displayInitialCards(String[] cards) {
        clearHands();
        for (int i = 0; i < cards.length; i++) {
            addCardToPlayer(cards[i]);
        }
    }

    private void addCardToPlayer(String cardStr) {
        JLabel cardLabel = getCardLabel(cardStr);
        playerCards.add(cardLabel);
        playerPanel.add(cardLabel);
        playerPanel.revalidate();
        playerPanel.repaint();
    }

    private void addCardToDealer(String cardStr) {
        JLabel cardLabel = getCardLabel(cardStr);
        dealerCards.add(cardLabel);
        dealerPanel.add(cardLabel);
        dealerPanel.revalidate();
        dealerPanel.repaint();
    }

    private JLabel getCardLabel(String cardStr) {
        ImageIcon cardImage = cardImages.get(cardStr);
        if (cardImage != null) {
            return new JLabel(cardImage);
        } else {
            return new JLabel(new ImageIcon(getClass().getResource("./images/back.png")));
        }
    }

    private void getCardImage() {
        String[] suits = { "Club", "Diamond", "Heart", "Spade" };
        String[] ranks = { "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A" };

        for (String suit : suits) {
            for (String rank : ranks) {
                String cardName = rank + "-" + suit;
                String imagePath = imageFolderPath + cardName + ".png";
                ImageIcon cardImage = createScaledImageIcon(imagePath, cardWidth, cardHeight);
                if (cardImage.getImage() != null) {
                    cardImages.put(cardName, cardImage);
                } else {
                    System.err.println("Card image not found: " + imagePath);
                    cardImages.put(cardName, new ImageIcon(getClass().getResource("/images/back.png")));
                }
            }
        }
        cardImages.put("back", new ImageIcon(getClass().getResource("/images/back.png")));
    }

    private void displayDealerHand(String message) {
        String handInfo = message.substring("DEALER_HAND ".length());
        String[] parts = handInfo.split("\\(");
        String cardDetails = parts[0].trim();
        String[] cards = cardDetails.split(" ");

        clearDealerHand();

        for (String cardStr : cards) {
            if (!cardStr.isEmpty()) {
                addCardToDealer(cardStr);
            }
        }
        if (parts.length > 1) {
            String scoreText = parts[1];
            if (scoreText.contains("Score:")) {
                dealerCardLabel.setText("(Dealer " + scoreText);
            } else {
                dealerCardLabel.setText("(Dealer Score: " + scoreText);
            }
        } else {
            dealerCardLabel.setText("Dealer: ");
        }

    }

    private void clearDealerHand() {
        dealerPanel.removeAll();
        dealerCards.clear();
        dealerPanel.add(dealerCardLabel);
        dealerPanel.revalidate();
        dealerPanel.repaint();
    }

    private void clearHands() {
        playerPanel.removeAll();
        playerCards.clear();
        dealerPanel.removeAll();
        dealerCards.clear();
        dealerPanel.add(dealerCardLabel);
        playerPanel.revalidate();
        playerPanel.repaint();
        dealerPanel.revalidate();
        dealerPanel.repaint();
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

    private ImageIcon createScaledImageIcon(String path, int width, int height) {
        ImageIcon icon = new ImageIcon(getClass().getResource(path));
        if (icon != null && icon.getImage() != null) {
            Image image = icon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
            return new ImageIcon(image);
        }
        return new ImageIcon(getClass().getResource("/images/back.png"));
    }

    public static void main(String[] args) {
        String serverAddress = (args.length == 0) ? "localhost" : args[0];
        int port = 10000;
        SwingUtilities.invokeLater(() -> new Client(serverAddress, port));
    }
}
