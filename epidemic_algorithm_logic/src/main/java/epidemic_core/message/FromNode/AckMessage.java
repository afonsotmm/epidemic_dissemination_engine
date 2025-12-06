package epidemic_core.message;

public class AckMessage {
    private Integer id;

    // Constructor
    public AckMessage(Integer id) {
        this.id = id;
    }

    public static AckMessage decodeMessage(String message) {
        String[] parts = message.split(";");
        return new AckMessage(Integer.parseInt(parts[1]));
    }

    // Getters
    public Integer getId() { return id; }
}
