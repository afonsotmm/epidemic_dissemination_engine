package epidemic_core.message.node_to_node.feedback;

import epidemic_core.message.common.MessageId;
import epidemic_core.message.common.MessageTopic;

public class FeedbackMsg {

    private final FeedbackHeader header;
    private final MessageId id;

    public FeedbackMsg(MessageId id) {
        this.header = new FeedbackHeader();
        this.id = id;
    }

    // getters
    public FeedbackHeader getHeader() { return header; }
    public MessageId getId() { return id; }

    public String encode() {
        return  header.direction().toString() + ";"
                + header.messageType().toString() + ";"
                + id.topic().subject() + ";"
                + id.timestamp() + ";"
                + id.topic().sourceId();
    }

    // decode
    public static FeedbackMsg decode(String raw) {
        String[] parts = raw.split(";");

        if (parts.length < 5) {
            throw new IllegalArgumentException("Invalid SpreadMsg");
        }

        MessageTopic topic = new MessageTopic(
                parts[2],
                Integer.parseInt(parts[4])
        );
        MessageId id = new MessageId(
                topic,
                Long.parseLong(parts[3])
        );

        return new FeedbackMsg(id);
    }

}
