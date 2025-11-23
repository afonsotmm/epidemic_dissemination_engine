package epidemic_core.message;

public class Message {

    private MessageType header; // request | reply
    private String subject;
    private Integer timeStamp;
    private String data;


    // Constructor
    public Message(MessageType header, String subject, Integer timeStamp, String data) {
        this.header = header;
        this.subject = subject;
        this.timeStamp = timeStamp;
        this.data = data;
    }


    // Getters
    public MessageType getHeader() { return header; }
    public String getSubject() { return subject; }
    public Integer getTimeStamp() { return timeStamp; }
    public String getData() { return data; }


    // Setters
    public void setHeader(MessageType header) { this.header = header; }
    public void setSubject(String subject) { this.subject = subject; }
    public void setTimeStamp(Integer timeStamp) { this.timeStamp = timeStamp; }
    public void setData(String data) { this.data = data; }


    // Encode and Decode message's Methods
    public String encodeMessage() {
        return header + ";" + subject + ";" + timeStamp + ";" + data;
    }

    public static Message decodeMessage(String message) {
        String[] parts = message.split(";");
        return new Message(MessageType.valueOf(parts[0]), parts[1], Integer.parseInt(parts[2]), parts[3]);
    }

}
