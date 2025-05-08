import java.io.*;
import java.net.*;
import java.util.*;

public class ClientHandler extends Thread {
    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;
    private List<Card> playerCards;
    private String playerName;
    private Server server;
    private int score;
    private int clientPort; // Store the port number of this client

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.playerCards = new ArrayList<>();
        this.server = server;
        this.score = 0;
        this.clientPort = socket.getLocalPort(); // Get the local port of the socket
        try {
            //input from client, output to client
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintWriter(socket.getOutputStream(), true);

            output.println("INPUT_NAME");
            this.playerName = input.readLine();
            server.log("New player: " + playerName + " has joined on port " + clientPort);
            server.broadcast(playerName + " JOINED");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getClientPort() {
        return clientPort;
    }

    @Override
    public void run() {
        try {
            output.println("WAITING_FOR_PLAYERS");
            String clientInput;
            while ((clientInput = input.readLine()) != null) {
                server.log(playerName + " says: " + clientInput + " (on port " + clientPort + ")");
                if (clientInput.equals("HIT") || clientInput.equals("STAND")) {
                    server.handleClientAction(this, clientInput);
                } else {
                    // Handle other commands if needed
                }
            }
        } catch (IOException e) {
            server.log(playerName + " disconnected from port " + clientPort);
            server.removeClient(this);
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public String getPlayerName() {
        return playerName;
    }

    public void sendInitialCards(Card card1, Card card2) {
        playerCards.add(card1);
        playerCards.add(card2);
        score = calculateScore();
        sendMessage("INITIAL_CARDS " + card1.toString() + " " + card2.toString());
        sendHand();
    }

    public void addCard(Card card) {
        playerCards.add(card);
        score = calculateScore();
        sendMessage("NEW_CARD " + card.toString()); // Inform client about the new card
        sendHand(); // Update the client's hand display
    }

    public int getScore() {
        return score;
    }

    private int calculateScore() {
        int total = 0;
        int aceCount = 0;

        for (Card c : playerCards) {
            int val = c.getValue();
            if (c.getRank().equals("A")) aceCount++;
            total += val;
        }

        while (aceCount > 0 && total > 21) {
            total -= 10;
            aceCount--;
        }

        return total;
    }

    public List<Card> getCards() {
        return playerCards;
    }

    public void sendHand() {
        StringBuilder sb = new StringBuilder("Your card: ");
        for (Card c : playerCards) {
            sb.append(c.toString()).append(" ");
        }
        sb.append("(Score: ").append(score).append(")");
        sendMessage(sb.toString());
    }

    public void sendMessage(String msg) {
        output.println(msg);
    }

    public void clearCards() {
        playerCards.clear();
        score = 0;
        sendMessage("CLEAR_HAND"); // Inform client to clear their hand display
    }
}