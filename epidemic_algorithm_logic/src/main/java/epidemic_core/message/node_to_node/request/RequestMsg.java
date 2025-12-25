package epidemic_core.message.node_to_node.request;

public class RequestMsg {

    private final RequestHeader header;
    private final int originId;

    public RequestMsg(int originId) {
        this.header = new RequestHeader();
        this.originId = originId;
    }

    // getters
    public RequestHeader getHeader() { return header; }
    public int getOriginId() { return originId; }

    // encode
    public String encode() {
        return  header.direction().toString() + ";"
                + header.messageType().toString() + ";"
                + originId;
    }

    // decode
    public static RequestMsg decode(String raw) {
        String[] parts = raw.split(";");

        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid RequestMsg");
        }

        int originId = Integer.parseInt(parts[2]);

        return new RequestMsg(originId);
    }
}
