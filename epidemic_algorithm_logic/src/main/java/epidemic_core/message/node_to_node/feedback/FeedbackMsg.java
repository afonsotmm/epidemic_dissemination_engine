package epidemic_core.message.node_to_node.feedback;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import epidemic_core.message.common.Direction;
import epidemic_core.message.common.MessageId;
import epidemic_core.message.node_to_node.NodeToNodeMessageType;
import java.io.IOException;

@JsonPropertyOrder({"direction", "messageType", "subject", "sourceId", "timestamp"})
public class FeedbackMsg {

    private final FeedbackHeader header;
    private final MessageId id;

    // Constructor
    @JsonCreator
    public FeedbackMsg(@JsonProperty("direction") String direction,
                       @JsonProperty("messageType") String messageType,
                       @JsonProperty("subject") String subject,
                       @JsonProperty("id") MessageId id,
                       @JsonProperty("timestamp") Long timestamp) {
        
        if (direction != null && !Direction.node_to_node.toString().equals(direction)) {
            throw new IllegalArgumentException("Invalid direction for FeedbackMsg: " + direction);
        }
        if (messageType != null && !NodeToNodeMessageType.feedback.toString().equals(messageType)) {
            throw new IllegalArgumentException("Invalid messageType for FeedbackMsg: " + messageType);
        }
        this.header = new FeedbackHeader();
        this.id = id;
    }

    // getters
    public FeedbackHeader getHeader() { return header; }
    public MessageId getId() { return id; }

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

    public static FeedbackMsg decodeMessage(String jsonString) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(jsonString, FeedbackMsg.class);
    }

}
