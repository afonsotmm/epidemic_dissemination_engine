package epidemic_core.message.node_to_node.initial_request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import epidemic_core.message.common.Direction;
import epidemic_core.message.node_to_node.NodeToNodeMessageType;
import java.io.IOException;

@JsonPropertyOrder({"direction", "messageType", "originId"})
public class InitialRequestMsg {

    private final InitialRequestHeader header;
    private final int originId;

    // Constructor from JSON
    @JsonCreator
    public InitialRequestMsg(@JsonProperty("direction") String direction,
                             @JsonProperty("messageType") String messageType,
                             @JsonProperty("originId") int originId) {
        
        if (direction != null && !Direction.node_to_node.toString().equals(direction)) {
            throw new IllegalArgumentException("Invalid direction for InitialRequestMsg: " + direction);
        }
        if (messageType != null && !NodeToNodeMessageType.initial_request.toString().equals(messageType)) {
            throw new IllegalArgumentException("Invalid messageType for InitialRequestMsg: " + messageType);
        }
        this.header = new InitialRequestHeader();
        this.originId = originId;
    }

    // getters
    public InitialRequestHeader getHeader() { return header; }
    public int getOriginId() { return originId; }

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

    public static InitialRequestMsg decodeMessage(String jsonString) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(jsonString, InitialRequestMsg.class);
    }

}
