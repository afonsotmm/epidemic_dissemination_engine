package epidemic_core.message.node_to_node.spread;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import epidemic_core.message.common.Direction;
import epidemic_core.message.common.MessageId;
import epidemic_core.message.common.MessageTopic;
import epidemic_core.message.node_to_node.NodeToNodeMessageType;
import java.io.IOException;

@JsonPropertyOrder({"direction", "messageType", "subject", "sourceId", "timestamp", "originId", "data"})
public class SpreadMsg {

    private final SpreadHeader header;
    private final MessageId id;
    private final int originId;
    private final String data;

    // Constructor
    @JsonCreator
    public SpreadMsg(@JsonProperty("direction") String direction,
                     @JsonProperty("messageType") String messageType,
                     @JsonProperty("subject") String subject,
                     @JsonProperty("sourceId") Integer sourceId,
                     @JsonProperty("timestamp") Long timestamp,
                     @JsonProperty("originId") int originId,
                     @JsonProperty("data") String data) {
        
        if (direction != null && !Direction.node_to_node.toString().equals(direction)) {
            throw new IllegalArgumentException("Invalid direction for SpreadMsg: " + direction);
        }
        if (messageType != null && !NodeToNodeMessageType.spread.toString().equals(messageType)) {
            throw new IllegalArgumentException("Invalid messageType for SpreadMsg: " + messageType);
        }
        this.header = new SpreadHeader();
        this.originId = originId;
        this.data = data;
        
        if (subject != null && sourceId != null && timestamp != null) {
            MessageTopic topic = new MessageTopic(subject, sourceId);
            this.id = new MessageId(topic, timestamp);
        } else {
            this.id = null;
        }
    }

    // getters
    public SpreadHeader getHeader() { return header; }
    public MessageId getId() { return id; }
    public int getOriginId() { return originId; }
    public String getData() { return data; }

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

    public static SpreadMsg decodeMessage(String jsonString) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(jsonString, SpreadMsg.class);
    }
}
