package epidemic_core.message.supervisor_to_node.start_round;

public class StartRoundMsg {

    private final StartRoundHeader header;

    public StartRoundMsg() {
        this.header = new StartRoundHeader();
    }

    // getters
    public StartRoundHeader getHeader() { return header; }

    // encode
    public String encode() {
        return  header.direction().toString() + ";" + header.messageType().toString();
    }

    // decode
    public static StartRoundMsg decode(String raw) {
        String[] parts = raw.split(";");

        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid RequestMsg");
        }

        return new StartRoundMsg();
    }

}
