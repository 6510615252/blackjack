import java.io.*;
import java.net.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class Client extends JFrame {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String serverAddress;
    private int serverPort;

    private JTextArea messageArea;
    private JTextField inputField;
    private JButton hitButton;
    private JButton standButton;
    private JPanel cardPanel;
    private JLabel dealerCardLabel;
    private boolean isMyTurn = false; // Added to track the client's turn

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
        setSize(400, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        messageArea = new JTextArea();
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

        // Disable buttons initially
        hitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isMyTurn) {
                    sendMessage("HIT");
                } else {
                    showMessage("It's not your turn!");
                }
            }
        });

        standButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isMyTurn) {
                    sendMessage("STAND");
                } else {
                    showMessage("It's not your turn!");
                }
            }
        });

        controlPanel.add(hitButton);
        controlPanel.add(standButton);

        cardPanel = new JPanel();
        cardPanel.setLayout(new FlowLayout());

        dealerCardLabel = new JLabel("Dealer: ?");

        add(scrollPane, BorderLayout.CENTER);
        add(inputField, BorderLayout.NORTH);
        add(controlPanel, BorderLayout.SOUTH);
        add(new JScrollPane(cardPanel), BorderLayout.EAST);
        add(dealerCardLabel, BorderLayout.WEST);

        setVisible(true);
    }

    private void receiveMessages() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.equals("INPUT_NAME")) {
                    continue;
                } else if (line.equals("GAME_START")) {
                    showMessage("Game started!");
                    isMyTurn = false;
                    updateControlButtons();
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
                    showMessage("Dealer's hand: " + line.substring("DEALER_HAND ".length()));
                } else if (line.startsWith("DEALER_HIT")) {
                    showMessage("Dealer hits: " + line.substring("DEALER_HIT ".length()));
                } else if (line.equals("YOUR_TURN")) {
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

    private void showMessage(String message) {
        SwingUtilities.invokeLater(() -> messageArea.append(message + "\n"));
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

