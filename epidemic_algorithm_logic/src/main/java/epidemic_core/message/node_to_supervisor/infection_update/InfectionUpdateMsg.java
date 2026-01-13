package epidemic_core.message.node_to_supervisor.infection_update;

import epidemic_core.message.common.MessageId;
import epidemic_core.message.common.MessageTopic;

public class InfectionUpdateMsg {

    private final InfectionUpdateHeader header;
    private final MessageId id;
    private final int updated_node_id;
    private final int infecting_node_id;
    private final String data;

    public InfectionUpdateMsg(
            MessageId id,
            int updated_node_id,
            int infecting_node_id,
            String data
    ) {
        this.header = new InfectionUpdateHeader();
        this.id = id;
        this.updated_node_id = updated_node_id;
        this.infecting_node_id = infecting_node_id;
        this.data = data;
    }

    // getters
    public InfectionUpdateHeader getHeader() { return header; }
    public MessageId getId() { return id; }
    public int getUpdatedNodeId() { return updated_node_id; }
    public int getInfectingNodeId() { return infecting_node_id; }
    public String getData() { return data; }

    public String encode() {
        return  header.direction().toString() + ";"
                + header.messageType().toString() + ";"
                + id.topic().subject() + ";"
                + id.timestamp() + ";"
                + id.topic().sourceId() + ";"
                + updated_node_id + ";"
                + infecting_node_id + ";"
                + data;
    }

    // decode
    public static InfectionUpdateMsg decode(String raw) {
        String[] parts = raw.split(";");

        if (parts.length < 8) {
            throw new IllegalArgumentException("Invalid InfectionUpdateMsg");
        }

        MessageTopic topic = new MessageTopic(
                parts[2],
                Integer.parseInt(parts[4])
        );
        MessageId id = new MessageId(
                topic,
                Long.parseLong(parts[3])
        );

        int updated_node_id = Integer.parseInt(parts[5]);
        int infecting_node_id = Integer.parseInt(parts[6]);
        String data = parts[7];

        return new InfectionUpdateMsg(id, updated_node_id, infecting_node_id, data);
    }

}
