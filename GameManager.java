import java.util.List;

public class GameManager {
    private Server server;
    private int currentPlayerIndex = 0;
    private boolean roundOver = false;

    public GameManager(Server server) {
        this.server = server;
    }

    public void startGame(List<ClientHandler> clients, Deck deck, DealerAI dealer) {
        currentPlayerIndex = 0;
        roundOver = false;

        for (ClientHandler player : clients) {
            Card card1 = deck.drawCard();
            Card card2 = deck.drawCard();
            player.sendInitialCards(card1, card2);
        }

        dealer.getCards().clear();
        try {
            dealer.drawCard();
            dealer.drawCard();
            server.broadcastDealerFirstCard(dealer.getCards().get(0));
        } catch (IllegalStateException e) {
            server.log("Error drawing dealer's initial cards: " + e.getMessage());
            server.broadcast("GAME_OVER: " + e.getMessage());
            return;
        }
    }

    public void startNewRound(List<ClientHandler> clients, Deck deck, DealerAI dealer) {
        currentPlayerIndex = 0;
        roundOver = false;

        if (deck.getRemainingCards().size() > ((clients.size() * 2) + 2)) {
            for (ClientHandler player : clients) {
                player.clearCards();
                Card card1 = deck.drawCard();
                Card card2 = deck.drawCard();
                player.sendInitialCards(card1, card2);
            }

            dealer.getCards().clear();
            dealer.drawCard();
            dealer.drawCard();
            server.broadcastDealerFirstCard(dealer.getCards().get(0));
            currentPlayerIndex--;
            moveToNextPlayer();
        }
    }

    public int getCurrentPlayerIndex() {
        return currentPlayerIndex;
    }

    public void handlePlayerAction(ClientHandler player, String action) {
        if (roundOver)
            return;

        if (action.equals("HIT")) {
            try {
                Card newCard = server.getDeck().drawCard();
                player.addCard(newCard);

                if (player.getScore() > 21) {
                    server.broadcastFromGameManager(player.getPlayerName() + " BUSTED!");
                    moveToNextPlayer();
                } else {
                    server.sendMessageToClient(player, "YOUR_TURN");
                }
            } catch (IllegalStateException e) {
                server.log("Error drawing card for " + player.getPlayerName() + ": " + e.getMessage());
                server.broadcastFromGameManager("GAME_OVER: " + e.getMessage());
                roundOver = true;
                server.enableNewRoundButton();
            }
        } else if (action.equals("STAND")) {
            server.broadcastFromGameManager(player.getPlayerName() + " STANDS");
            moveToNextPlayer();
        }
    }

    private void moveToNextPlayer() {
        List<ClientHandler> clients = server.getClients();
        currentPlayerIndex++;
        if (currentPlayerIndex < clients.size()) {
            server.sendMessageToClient(clients.get(currentPlayerIndex), "YOUR_TURN");
        } else {
            dealerPlay();
        }
    }

    private void dealerPlay() {
        roundOver = true;
        server.broadcastFromGameManager("DEALER_TURN");
        server.broadcastFromGameManager("DEALER_HAND " + getDealerHandString() + " (Score: " + getDealerScore() + ")");

        while (getDealerScore() < 17) {
            try {
                Card newCard = server.getDeck().drawCard();
                getDealer().addCard(newCard);
                server.broadcastFromGameManager("DEALER_HIT " + newCard.toString());
                server.broadcastFromGameManager(
                        "DEALER_HAND " + getDealerHandString() + " (Score: " + getDealerScore() + ")");
            } catch (IllegalStateException e) {
                server.log("Error drawing card for dealer: " + e.getMessage());
                server.broadcastFromGameManager("GAME_OVER: " + e.getMessage());
                break;
            }
        }

        if (getDealerScore() > 21) {
            server.broadcastFromGameManager("DEALER BUSTED!");
        }

        determineWinners();
        server.enableNewRoundButton();
    }

    private void determineWinners() {
        int dealerScore = getDealerScore();
        for (ClientHandler player : server.getClients()) {
            int playerScore = player.getScore();
            if (playerScore > 21) {
                server.broadcastFromGameManager(player.getPlayerName() + " LOSES (Bust)");
            } else if (dealerScore > 21) {
                server.broadcastFromGameManager(player.getPlayerName() + " WINS (Dealer Bust)");
            } else if (playerScore > dealerScore) {
                server.broadcastFromGameManager(player.getPlayerName() + " WINS");
            } else if (playerScore < dealerScore) {
                server.broadcastFromGameManager(player.getPlayerName() + " LOSES");
            } else {
                server.broadcastFromGameManager(player.getPlayerName() + " PUSH (Tie)");
            }
        }
    }

    private String getDealerHandString() {
        return getDealer().getCardsAsString();
    }

    private int getDealerScore() {
        return getDealer().getScore();
    }

    private DealerAI getDealer() {
        return server.getDealer();
    }
}