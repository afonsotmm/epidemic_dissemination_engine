package epidemic_core.message.node_to_supervisor.remotion_update;

import epidemic_core.message.common.MessageId;

public class RemotionUpdateMsg {

    private final RemotionUpdateHeader header;
    private final MessageId id;
    private final int updated_node_id;

    public RemotionUpdateMsg(
            MessageId id,
            int updated_node_id
    ) {
        this.header = new RemotionUpdateHeader();
        this.id = id;
        this.updated_node_id = updated_node_id;
    }

    // getters
    public RemotionUpdateHeader getHeader() { return header; }
    public MessageId getId() { return id; }
    public int getUpdatedNodeId() { return updated_node_id; }

    public String encode() {
        return  header.direction().toString() + ";"
                + header.messageType().toString() + ";"
                + id.subject() + ";"
                + id.timestamp() + ";"
                + id.sourceId() + ";"
                + updated_node_id;
    }

    // decode
    public static RemotionUpdateMsg decode(String raw) {
        String[] parts = raw.split(";");

        if (parts.length < 6) {
            throw new IllegalArgumentException("Invalid SpreadMsg");
        }

        MessageId id = new MessageId(
                parts[2],
                Long.parseLong(parts[3]),
                Integer.parseInt(parts[4])
        );

        int updated_node_id = Integer.parseInt(parts[5]);

        return new RemotionUpdateMsg(id, updated_node_id);
    }

}
