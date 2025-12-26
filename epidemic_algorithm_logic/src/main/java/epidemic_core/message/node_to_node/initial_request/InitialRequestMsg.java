package epidemic_core.message.node_to_node.initial_request;

public class InitialRequestMsg {

    private final InitialRequestHeader header;
    private final int originId;

    public InitialRequestMsg(int originId) {
        this.header = new InitialRequestHeader();
        this.originId = originId;
    }

    // getters
    public InitialRequestHeader getHeader() { return header; }
    public int getOriginId() { return originId; }

    public String encode() {
        return  header.direction().toString() + ";"
                + header.messageType().toString() + ";"
                + originId;
    }

    // decode
    public static InitialRequestMsg decode(String raw) {
        String[] parts = raw.split(";");

        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid InitialRequestMsg");
        }

        int originId = Integer.parseInt(parts[2]);

        return new InitialRequestMsg(originId);
    }

}
