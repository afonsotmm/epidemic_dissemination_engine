package epidemic_core.message.node_to_node.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import epidemic_core.message.common.Direction;
import epidemic_core.message.common.MessageId;
import epidemic_core.message.common.MessageTopic;
import epidemic_core.message.node_to_node.NodeToNodeMessageType;
import java.io.IOException;

@JsonPropertyOrder({"direction", "messageType", "subject", "sourceId", "timestamp", "originId"})
public class RequestMsg {

    private final RequestHeader header;
    private final MessageId id;
    private final int originId;

    // Constructor
    @JsonCreator
    public RequestMsg(@JsonProperty("direction") String direction,
                      @JsonProperty("messageType") String messageType,
                      @JsonProperty("subject") String subject,
                      @JsonProperty("sourceId") Integer sourceId,
                      @JsonProperty("timestamp") Long timestamp,
                      @JsonProperty("originId") int originId) {
        
        if (direction != null && !Direction.node_to_node.toString().equals(direction)) {
            throw new IllegalArgumentException("Invalid direction for RequestMsg: " + direction);
        }
        if (messageType != null && !NodeToNodeMessageType.request.toString().equals(messageType)) {
            throw new IllegalArgumentException("Invalid messageType for RequestMsg: " + messageType);
        }
        this.header = new RequestHeader();
        this.originId = originId;
        
        if (subject != null && sourceId != null && timestamp != null) {
            MessageTopic topic = new MessageTopic(subject, sourceId);
            this.id = new MessageId(topic, timestamp);
        } else {
            this.id = null;
        }
    }

    // getters
    public RequestHeader getHeader() { return header; }
    public MessageId getId() { return id; }
    public int getOriginId() { return originId; }

    @JsonProperty("direction")
    public String getDirection() {
        return header.direction().toString();
    }

    @JsonProperty("messageType")
    public String getMessageType() {
        return header.messageType().toString();
    }

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

    public String encode() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(this);
    }

    public static RequestMsg decodeMessage(String jsonString) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(jsonString, RequestMsg.class);
    }
}
