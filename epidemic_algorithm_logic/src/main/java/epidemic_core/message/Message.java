package epidemic_core.message;

//  Represents a message in the epidemic dissemination system.
//  The timeStamp field serves as a logical counter to determine message freshness.
//  Higher values indicate more recent messages.

public class Message {

    private String subject;   
    private Integer timeStamp;  // Logical counter
    private String data;       

    // Constructor
    public Message(String subject, Integer timeStamp, String data) {
        this.subject = subject;
        this.timeStamp = timeStamp;
        this.data = data;
    }

    // Getters
    public String getSubject() { return subject; }
    public Integer getTimeStamp() { return timeStamp; }
    public String getData() { return data; }

    // Setters
    public void setSubject(String subject) { this.subject = subject; }
    public void setTimeStamp(Integer timeStamp) { this.timeStamp = timeStamp; }
    public void setData(String data) { this.data = data; }

    // Encode and Decode message's Methods
    public String encodeMessage(MessageType header) {
        return header + ";" + subject + ";" + timeStamp + ";" + data;
    }

    public static Message decodeMessage(String message) {
        String[] parts = message.split(";");
        return new Message(parts[1], Integer.parseInt(parts[2]), parts[3]);
    }

}
