import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import java.awt.*;
import java.util.List;

public class Server extends JFrame {

    private ServerSocket serverSocket;
    private final int initialPort = 10000;
    private int maxPlayers;
    private List<ClientHandler> clients = new ArrayList<>(); 
    private Deck deck; 
    private boolean gameStarted = false; 
    private DealerAI dealer; 
    private GameManager gameManager; 
    private Set<Integer> usedPorts = new HashSet<>(); 

    private JTextArea logArea; // Text area to display server logs
    private JButton startButton; // Button to start the game
    private JLabel playerCountLabel; // Label to display the number of connected players
    private JButton newRoundButton; // Button to start a new round

    public Server(int maxPlayers) {
        super("Blackjack Server");
        this.maxPlayers = maxPlayers;
        setupGUI(); 
        setupServer(); 
        gameManager = new GameManager(this); 
        usedPorts.add(initialPort); 
    }

    private void setupGUI() {
        setSize(500, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scroll = new JScrollPane(logArea);

        startButton = new JButton("Start");
        startButton.setEnabled(false);
        newRoundButton = new JButton("New Round");
        newRoundButton.setEnabled(false);
        newRoundButton.addActionListener(e -> startNewRound()); // Add action listener for the new round button
        JPanel controlPanel = new JPanel();
        controlPanel.add(startButton);
        controlPanel.add(newRoundButton); // Add the new round button to the control panel

        playerCountLabel = new JLabel("Connected Player: 0/" + maxPlayers);

        startButton.addActionListener(e -> startGame()); // Add action listener to the start button

        add(scroll, BorderLayout.CENTER);
        add(playerCountLabel, BorderLayout.NORTH);
        add(controlPanel, BorderLayout.SOUTH); // Add the control panel with both buttons

        setVisible(true);
    }

    private void setupServer() {
        try {
            // Create server socket port 10000
            serverSocket = new ServerSocket(initialPort);
            log("Server waiting for players at initial port " + initialPort);
            // Create new Thread
            Thread acceptThread = new Thread(() -> {
                while (!gameStarted && clients.size() < maxPlayers) {
                    try {
                        Socket initialClientSocket = serverSocket.accept();
                        int newClientPort = findAvailablePort();

                        if (newClientPort != -1) {
                            // Create OutputStream Inform the client to reconnect to the new port
                            PrintWriter outToClient = new PrintWriter(initialClientSocket.getOutputStream(), true);
                            outToClient.println("NEW_PORT " + newClientPort);

                            ServerSocket clientServerSocket = new ServerSocket(newClientPort);
                            Socket clientSocket = clientServerSocket.accept();

                            ClientHandler client = new ClientHandler(clientSocket, this);
                            clients.add(client);
                            new Thread(client).start();
                            log("New player connected on port " + newClientPort + ": " + clientSocket.getInetAddress()
                                    + " (" + client.getPlayerName() + ")");
                            updatePlayerCount();

                            outToClient.close();
                            initialClientSocket.close();
                            clientServerSocket.close();
                        }
                        // Error handling
                        else {
                            log("No available port for new client. Connection refused.");
                            PrintWriter outToClient = new PrintWriter(initialClientSocket.getOutputStream(), true);
                            outToClient.println("SERVER_FULL");
                            outToClient.close();
                            initialClientSocket.close();
                        }
                    } catch (IOException e) {
                        log("Error with client connection: " + e.getMessage());
                    }
                }
                if (clients.size() == maxPlayers && !gameStarted) {
                    SwingUtilities.invokeLater(() -> startButton.setEnabled(true));
                    log("All players have joined (" + maxPlayers + " players), ready to start.");
                }
            });
            acceptThread.start();

        } catch (IOException e) {
            log("Can't start server: " + e.getMessage());
        }
    }

    private int findAvailablePort() {
        int port = initialPort + 1;
        while (port < 65535) {
            if (!usedPorts.contains(port)) {
                try (ServerSocket ss = new ServerSocket(port)) {
                    usedPorts.add(port);
                    return port;
                } catch (IOException e) {
                }
            }
            port++;
        }
        return -1;
    }

    public void updatePlayerCount() {
        playerCountLabel.setText("Connected player: " + clients.size() + "/" + maxPlayers);
        if (clients.size() == maxPlayers && !gameStarted) {
            startButton.setEnabled(true);
            log("All players have joined (" + maxPlayers + " players), ready to start.");
        }
    }

    private void startGame() {
        if (gameStarted)
            return;
        gameStarted = true;
        startButton.setEnabled(false);
        newRoundButton.setEnabled(true);
        log("Starting Blackjack game with " + clients.size() + " players!");
        broadcast("GAME_START");

        deck = new Deck();
        deck.shuffle();
        dealer = new DealerAI(deck);

        gameManager.startGame(clients, deck, dealer);

        if (!clients.isEmpty()) {
            clients.get(0).sendMessage("YOUR_TURN");
        }
    }

    private void startNewRound() {
        if (!gameStarted)
            return;
        gameStarted = false;
        newRoundButton.setEnabled(false);
        if (deck.getRemainingCards().size() > 1) {
            deck.shuffle();
            dealer = new DealerAI(deck);
            gameManager.startNewRound(clients, deck, dealer);
            gameStarted = true;
            newRoundButton.setEnabled(true);
            return;
        }
        log("Starting new round");
        broadcast("DECK RAN  OUT OF CARD");
    }

    public void enableNewRoundButton() {
        SwingUtilities.invokeLater(() -> newRoundButton.setEnabled(true));
    }

    public void broadcastDealerFirstCard(Card card) {
        broadcast("DEALER_FIRST_CARD " + card.toString());
    }

    public void broadcast(String message) {
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }

    public Deck getDeck() {
        return deck;
    }

    public void log(String message) {
        SwingUtilities.invokeLater(() -> logArea.append(message + "\n"));
    }

    public void handleClientAction(ClientHandler client, String action) {
        gameManager.handlePlayerAction(client, action);
    }

    public void removeClient(ClientHandler client) {
        clients.remove(client);
        usedPorts.remove(client.getClientPort());
        updatePlayerCount();
        broadcast(client.getPlayerName() + " LEFT");
    }

    public void sendMessageToClient(ClientHandler client, String message) {
        client.sendMessage(message);
    }

    public void broadcastFromGameManager(String message) {
        broadcast(message);
    }

    public List<ClientHandler> getClients() {
        return clients;
    }

    public DealerAI getDealer() {
        return dealer;
    }

    public static void main(String[] args) {
        String maxPlayersStr = JOptionPane.showInputDialog("Enter maximum number of players:", "2");
        try {
            int maxPlayers = Integer.parseInt(maxPlayersStr);
            if (maxPlayers > 0) {
                SwingUtilities.invokeLater(() -> new Server(maxPlayers));
            } else {
                JOptionPane.showMessageDialog(null, "Invalid number of players.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(null, "Invalid input for number of players.", "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}