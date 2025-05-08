import java.util.*;

public class DealerAI {
    private List<Card> dealerCards;
    private int dealerScore;
    private Deck deck;

    public DealerAI(Deck deck) {
        this.deck = deck;
        dealerCards = new ArrayList<>();
        dealerScore = 0;
    }

    public void addCard(Card card) { 
        dealerCards.add(card);
        dealerScore = calculateScore();
    }

    public void drawCard() {
        Card c = deck.drawCard();
        dealerCards.add(c);
        dealerScore = calculateScore();
    }

    public int getScore() {
        return dealerScore;
    }

    private int calculateScore() {
        int total = 0;
        int aceCount = 0;

        for (Card c : dealerCards) {
            int val = c.getValue();
            if (c.getRank().equals("A")) 
            aceCount++;
            total += val;
        }

        while (aceCount > 0 && total > 21) {
            total -= 10;
            aceCount--;
        }

        return total;
    }

    public List<Card> getCards() {
        return dealerCards;
    }

    public String getCardsAsString() {
        StringBuilder sb = new StringBuilder();
        for (Card c : dealerCards) {
            sb.append(c.toString()).append(" ");
        }
        return sb.toString().trim();
    }
}