package epidemic_core.message.supervisor_to_ui.infection_update;

import epidemic_core.message.common.MessageId;
import epidemic_core.message.common.MessageTopic;

public class InfectionUpdateMsg {

    private final InfectionUpdateHeader header;
    private final MessageId id;
    private final int updated_node_id;
    private final int infecting_node_id;

    public InfectionUpdateMsg(MessageId id, int updated_node_id, int infecting_node_id) {
        this.header = new InfectionUpdateHeader();
        this.id = id;
        this.updated_node_id = updated_node_id;
        this.infecting_node_id = infecting_node_id;
    }

    // getters
    public InfectionUpdateHeader getHeader() { return header; }
    public MessageId getId() { return id; }
    public int getUpdatedNodeId() { return updated_node_id; }
    public int getInfectingNodeId() { return infecting_node_id; }

    public String encode() {
        return  header.direction().toString() + ";"
                + header.messageType().toString() + ";"
                + id.topic().subject() + ";"
                + id.timestamp() + ";"
                + id.topic().sourceId() + ";"
                + updated_node_id + ";"
                + infecting_node_id;
    }
}
