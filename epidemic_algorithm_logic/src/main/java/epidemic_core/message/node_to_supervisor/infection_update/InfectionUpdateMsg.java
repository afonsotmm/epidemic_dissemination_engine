package epidemic_core.message.node_to_supervisor.infection_update;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import epidemic_core.message.common.Direction;
import epidemic_core.message.common.MessageId;
import epidemic_core.message.common.MessageTopic;
import epidemic_core.message.node_to_supervisor.NodeToSupervisorMessageType;
import java.io.IOException;

/**
 * Message Node -> Supervisor: Infected node and node who infected
 */

@JsonPropertyOrder({"direction", "messageType", "updated_node_id", "infecting_node_id", "subject", "sourceId", "timestamp", "data"})
public class InfectionUpdateMsg {

    private final InfectionUpdateHeader header;
    private final MessageId id;
    private final int updated_node_id;
    private final int infecting_node_id;
    private final String data;

    @JsonCreator
    public InfectionUpdateMsg(@JsonProperty("direction") String direction,
                               @JsonProperty("messageType") String messageType,
                               @JsonProperty("updated_node_id") int updated_node_id,
                               @JsonProperty("infecting_node_id") int infecting_node_id,
                               @JsonProperty("subject") String subject,
                               @JsonProperty("sourceId") Integer sourceId,
                               @JsonProperty("timestamp") Long timestamp,
                               @JsonProperty("data") String data) {
                                
        if (direction != null && !Direction.node_to_supervisor.toString().equals(direction)) {
            throw new IllegalArgumentException("Invalid direction for InfectionUpdateMsg: " + direction);
        }
        if (messageType != null && !NodeToSupervisorMessageType.infection_update.toString().equals(messageType)) {
            throw new IllegalArgumentException("Invalid messageType for InfectionUpdateMsg: " + messageType);
        }
        this.header = new InfectionUpdateHeader();
        this.updated_node_id = updated_node_id;
        this.infecting_node_id = infecting_node_id;
        this.data = data;
        
        // Constroi MessageId a partir dos campos planos
        if (subject != null && sourceId != null && timestamp != null) {
            MessageTopic topic = new MessageTopic(subject, sourceId);
            this.id = new MessageId(topic, timestamp);
        } else {
            this.id = null;
        }
    }

    // Getters
    public InfectionUpdateHeader getHeader() { return header; }
    public MessageId getId() { return id; }

    @JsonProperty("direction")
    public String getDirection() {
        return header.direction().toString();
    }

    @JsonProperty("messageType")
    public String getMessageType() {
        return header.messageType().toString();
    }

    @JsonProperty("updated_node_id")
    public int getUpdatedNodeId() { return updated_node_id; }

    @JsonProperty("infecting_node_id")
    public int getInfectingNodeId() { return infecting_node_id; }

    @JsonProperty("subject")
    public String getSubject() {
        return id != null ? id.topic().subject() : null;
    }

    @JsonProperty("sourceId")
    public Integer getSourceId() {
        return id != null ? id.topic().sourceId() : null;
    }

    @JsonProperty("timestamp")
    public Long getTimestamp() {
        return id != null ? id.timestamp() : null;
    }

    @JsonProperty("data")
    public String getData() { return data; }

    public String encode() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(this);
    }

    public static InfectionUpdateMsg decodeMessage(String jsonString) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(jsonString, InfectionUpdateMsg.class);
    }

}
