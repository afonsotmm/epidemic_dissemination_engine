package epidemic_core.message.node_to_node.request_and_spread;

import epidemic_core.message.common.MessageId;
import epidemic_core.message.common.MessageTopic;

public class RequestAndSpreadMsg {

    private final RequestAndSpreadHeader header;
    private final MessageId id;
    private final int originId;
    private final String data;

    public RequestAndSpreadMsg(
            MessageId id,
            int originId,
            String data
    ) {
        this.header = new RequestAndSpreadHeader();
        this.id = id;
        this.originId = originId;
        this.data = data;
    }

    // getters
    public RequestAndSpreadHeader getHeader() { return header; }
    public MessageId getId() { return id; }
    public int getOriginId() { return originId; }
    public String getData() { return data; }

    // encode
    public String encode() {
        return  header.direction().toString() + ";"
                + header.messageType().toString() + ";"
                + id.topic().subject() + ";"
                + id.timestamp() + ";"
                + id.topic().sourceId() + ";"
                + originId + ";"
                + data;
    }

    // decode
    public static RequestAndSpreadMsg decode(String raw) {
        String[] parts = raw.split(";");

        if (parts.length < 7) {
            throw new IllegalArgumentException("Invalid RequestAndSpreadMsg");
        }

        MessageTopic topic = new MessageTopic(
                parts[2],
                Integer.parseInt(parts[4])
        );
        MessageId id = new MessageId(
                topic,
                Long.parseLong(parts[3])
        );

        int originId = Integer.parseInt(parts[5]);
        String data = parts[6];

        return new RequestAndSpreadMsg(id, originId, data);
    }
}
