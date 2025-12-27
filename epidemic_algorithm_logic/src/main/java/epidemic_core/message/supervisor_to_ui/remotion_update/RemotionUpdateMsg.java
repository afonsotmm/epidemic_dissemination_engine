package epidemic_core.message.supervisor_to_ui.remotion_update;

import epidemic_core.message.common.MessageId;
import epidemic_core.message.common.MessageTopic;

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
                + id.topic().subject() + ";"
                + id.timestamp() + ";"
                + id.topic().sourceId() + ";"
                + updated_node_id;
    }
}
