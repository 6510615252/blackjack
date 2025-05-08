import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import java.awt.*;
import java.util.List;

public class Server extends JFrame {

    private ServerSocket serverSocket;
    private final int initialPort = 10000;
    private int maxPlayers; // Maximum number of players allowed
    private List<ClientHandler> clients = new ArrayList<>(); // List to store connected clients
    private Deck deck; // Deck of cards for the game
    private boolean gameStarted = false; // Flag indicating if the game has started
    private DealerAI dealer; // Dealer AI for the game
    private GameManager gameManager; // Manages the game logic
    private Set<Integer> usedPorts = new HashSet<>(); // Stores ports that are currently in use

    private JTextArea logArea; // Text area to display server logs
    private JButton startButton; // Button to start the game
    private JLabel playerCountLabel; // Label to display the number of connected players
    private JButton newRoundButton; // Button to start a new round


    public Server(int maxPlayers) {
        super("Blackjack Server");
        this.maxPlayers = maxPlayers;
        setupGUI(); // Initialize the graphical user interface
        setupServer(); // Set up the server socket and connection handling
        gameManager = new GameManager(this); // Create the game manager instance
        usedPorts.add(initialPort); // Add the initial port to the set of used ports
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
            //Create server socket and bind at port 10000
            serverSocket = new ServerSocket(initialPort);
            log("Server waiting for players at initial port " + initialPort);
            //Create new Thread
            Thread acceptThread = new Thread(() -> {
                while (!gameStarted && clients.size() < maxPlayers) {
                    try {
                        //Wait for client to connect to port 10000
                        Socket initialClientSocket = serverSocket.accept();
                        // Determine a new port for this client
                        int newClientPort = findAvailablePort();
                        if (newClientPort != -1) {
                            // Inform the client to reconnect to the new port
                            PrintWriter outToClient = new PrintWriter(initialClientSocket.getOutputStream(), true);
                            outToClient.println("NEW_PORT " + newClientPort);

                            // Create a new ServerSocket for this specific client
                            ServerSocket clientServerSocket = new ServerSocket(newClientPort);
                            Socket clientSocket = clientServerSocket.accept(); // Wait for the client to reconnect

                            ClientHandler client = new ClientHandler(clientSocket, this);
                            clients.add(client);
                            new Thread(client).start();
                            log("New player connected on port " + newClientPort + ": " + clientSocket.getInetAddress() + " (" + client.getPlayerName() + ")");
                            updatePlayerCount();

                            // Close the initial socket and the temporary client server socket
                            outToClient.close();
                            initialClientSocket.close();
                            clientServerSocket.close();
                        }
                        //Error handling
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
                    // Port is already in use, try the next one
                }
            }
            port++;
        }
        return -1; // No available port found
    }

    public void updatePlayerCount() {
        playerCountLabel.setText("Connected player: " + clients.size() + "/" + maxPlayers);
        if (clients.size() == maxPlayers && !gameStarted) {
            startButton.setEnabled(true);
            log("All players have joined (" + maxPlayers + " players), ready to start.");
        }
    }

    private void startGame() {
        if (gameStarted) return;
        gameStarted = true;
        startButton.setEnabled(false);
        newRoundButton.setEnabled(true); // Enable the new round button when the game starts
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
        if (!gameStarted) return;
        gameStarted = false;
        newRoundButton.setEnabled(false); // Disable the new round button during the round setup
        log("Starting new round");
        broadcast("NEW_ROUND_START");
        deck = new Deck(); // Create a new deck for the new round
        deck.shuffle();
        dealer = new DealerAI(deck);
        gameManager.startNewRound(clients, deck, dealer); // Call a new method in GameManager for new round

        if (!clients.isEmpty()) {
            clients.get(gameManager.getCurrentPlayerIndex()).sendMessage("YOUR_TURN"); // Send turn to the first player
        }
        gameStarted = true;
        newRoundButton.setEnabled(true); // Enable the new round button after the round starts
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
        usedPorts.remove(client.getClientPort()); // Release the port when the client disconnects
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
            JOptionPane.showMessageDialog(null, "Invalid input for number of players.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}