package epidemic_core.message.node_to_supervisor.remotion_update;

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
 * Message Node -> Supervisor: ID of the removed node and with what message it was removed
 */

@JsonPropertyOrder({"direction", "messageType", "updated_node_id", "subject", "sourceId", "timestamp"})
public class RemotionUpdateMsg {

    private final RemotionUpdateHeader header;
    private final MessageId id;
    private final int updated_node_id;

    @JsonCreator
    public RemotionUpdateMsg(@JsonProperty("direction") String direction,
                             @JsonProperty("messageType") String messageType,
                             @JsonProperty("updated_node_id") int updated_node_id,
                             @JsonProperty("subject") String subject,
                             @JsonProperty("sourceId") Integer sourceId,
                             @JsonProperty("timestamp") Long timestamp) {

        if (direction != null && !Direction.node_to_supervisor.toString().equals(direction)) {
            throw new IllegalArgumentException("Invalid direction for RemotionUpdateMsg: " + direction);
        }
        if (messageType != null && !NodeToSupervisorMessageType.remotion_update.toString().equals(messageType)) {
            throw new IllegalArgumentException("Invalid messageType for RemotionUpdateMsg: " + messageType);
        }
        this.header = new RemotionUpdateHeader();
        this.updated_node_id = updated_node_id;
        
        if (subject != null && sourceId != null && timestamp != null) {
            MessageTopic topic = new MessageTopic(subject, sourceId);
            this.id = new MessageId(topic, timestamp);
        } else {
            this.id = null;
        }
    }

    // getters
    public RemotionUpdateHeader getHeader() { return header; }
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

    // --------------------------------------------------

    public String encode() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(this);
    }

    public static RemotionUpdateMsg decodeMessage(String jsonString) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(jsonString, RemotionUpdateMsg.class);
    }
}
