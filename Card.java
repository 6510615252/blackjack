public class Card {

    private String suit;  
    private String rank;  

    public Card(String suit, String rank) {
        this.suit = suit;
        this.rank = rank;
    }

    public int getValue() {
        return switch (rank) {
            case "A" -> 11;
            case "K", "Q", "J" -> 10;
            default -> Integer.parseInt(rank);
        };
    }

    @Override
    public String toString() {
        return rank + suit;
    }

    public String getSuit() {
        return suit;
    }

    public String getRank() {
        return rank;
    }
}
