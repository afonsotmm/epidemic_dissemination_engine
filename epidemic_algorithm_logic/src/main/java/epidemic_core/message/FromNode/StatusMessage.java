package epidemic_core.message;

public class StatusMessage {
    private Integer id;
    private String subject;
    private String status;

    // Constructor
    public StatusMessage(Integer id, String subject, String status) {
        this.subject = subject;
        this.id = id;
        this.status = status;
    }

    public static StatusMessage decodeMessage(String message) {
        String[] parts = message.split(";");
        return new StatusMessage(Integer.parseInt(parts[1]), parts[2], parts[3]);
    }

    // Getters
    public Integer getId() { return id; }
    public String getSubject() { return subject; }
    public String getStatus() { return status; }
}
