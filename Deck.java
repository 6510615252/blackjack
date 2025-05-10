import java.util.*;

public class Deck {

    private List<Card> cards;
    private int currentIndex;

    public Deck() {
        cards = new ArrayList<>();
        String[] suits = {"-Spade", "-Heart", "-Diamond", "-Club"};
        String[] ranks = {
            "2", "3", "4", "5", "6", "7", "8", "9", "10",
            "J", "Q", "K", "A"
        };

        for (String suit : suits) {
            for (String rank : ranks) {
                cards.add(new Card(suit, rank));
            }
        }

        shuffle();
        currentIndex = 0;
    }

    public void shuffle() {
        Collections.shuffle(cards);
        currentIndex = 0;
    }

    public Card drawCard() {
        if (currentIndex < cards.size()) {
            return cards.get(currentIndex++);
        } else {
            throw new IllegalStateException("Run out of card!");
        }
    }

    public int cardsLeft() {
        return cards.size() - currentIndex;
    }

    public List<Card> getUsedCards() {
        return cards.subList(0, currentIndex);
    }

    public List<Card> getRemainingCards() {
        return cards.subList(currentIndex, cards.size());
    }
}
