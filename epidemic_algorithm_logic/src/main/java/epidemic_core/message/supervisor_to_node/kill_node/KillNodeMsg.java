package epidemic_core.message.supervisor_to_node.kill_node;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import epidemic_core.message.common.Direction;
import epidemic_core.message.supervisor_to_node.SupervisorToNodeMessageType;

import java.io.IOException;

/**
 * Message Supervisor -> Node: stop the node
 */
@JsonPropertyOrder({"direction", "messageType"})
public class KillNodeMsg {

    private final KillNodeHeader header;

    @JsonCreator
    public KillNodeMsg(@JsonProperty("direction") String direction,
                      @JsonProperty("messageType") String messageType) {
        
        if (direction != null && !Direction.supervisor_to_node.toString().equals(direction)) {
            throw new IllegalArgumentException("Invalid direction for KillNodeMsg: " + direction);
        }
        if (messageType != null && !SupervisorToNodeMessageType.kill_node.toString().equals(messageType)) {
            throw new IllegalArgumentException("Invalid messageType for KillNodeMsg: " + messageType);
        }
        this.header = new KillNodeHeader();
    }

    // Getters
    public KillNodeHeader getHeader() { return header; }

    @JsonProperty("direction")
    public String getDirection() {
        return header.direction().toString();
    }

    @JsonProperty("messageType")
    public String getMessageType() {
        return header.messageType().toString();
    }

    public String encode() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(this);
    }

    public static KillNodeMsg decodeMessage(String jsonString) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(jsonString, KillNodeMsg.class);
    }
}
