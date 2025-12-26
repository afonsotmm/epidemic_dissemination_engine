package epidemic_core.message.node_to_node.request;

import epidemic_core.message.common.MessageId;
import epidemic_core.message.common.MessageTopic;

public class RequestMsg {

    private final RequestHeader header;
    private final MessageId id;
    private final int originId;

    public RequestMsg(
            MessageId id,
            int originId
    ) {
        this.header = new RequestHeader();
        this.id = id;
        this.originId = originId;
    }

    // getters
    public RequestHeader getHeader() { return header; }
    public MessageId getId() { return id; }
    public int getOriginId() { return originId; }

    public String encode() {
        return  header.direction().toString() + ";"
                + header.messageType().toString() + ";"
                + id.topic().subject() + ";"
                + id.timestamp() + ";"
                + id.topic().sourceId() + ";"
                + originId;
    }

    // decode
    public static RequestMsg decode(String raw) {
        String[] parts = raw.split(";");

        if (parts.length < 6) {
            throw new IllegalArgumentException("Invalid RequestMsg");
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

        return new RequestMsg(id, originId);
    }
}
