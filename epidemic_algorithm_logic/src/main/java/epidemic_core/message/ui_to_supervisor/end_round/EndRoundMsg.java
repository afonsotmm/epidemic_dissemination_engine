package epidemic_core.message.ui_to_supervisor.end_round;

public class EndRoundMsg {

    private final EndRoundHeader header;

    // Constructor
    public EndRoundMsg(){
        this.header = new EndRoundHeader();
    }

    // encode
    public String encode() {
        return  header.direction().toString() + ";" + header.messageType().toString();
    }

    // decode
    public static EndRoundMsg decodeMessage(String raw) {
        String[] parts = raw.split(";");

        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid RequestMsg");
        }

        return new EndRoundMsg();
    }
}
