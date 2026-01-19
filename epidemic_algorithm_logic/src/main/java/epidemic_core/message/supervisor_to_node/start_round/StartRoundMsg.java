package epidemic_core.message.supervisor_to_node.start_round;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import epidemic_core.message.common.Direction;
import epidemic_core.message.supervisor_to_node.SupervisorToNodeMessageType;
import java.io.IOException;

/**
 * Message Supervisor -> Node: Start round signal
 */
@JsonPropertyOrder({ "direction", "messageType" })
public class StartRoundMsg {

    private final StartRoundHeader header;

    // Constructor
    @JsonCreator
    public StartRoundMsg(@JsonProperty("direction") String direction,
            @JsonProperty("messageType") String messageType) {

        if (direction != null && !Direction.supervisor_to_node.toString().equals(direction)
                && !Direction.supervisor_to_ui.toString().equals(direction)) {
            throw new IllegalArgumentException("Invalid direction for StartRoundMsg: " + direction);
        }
        if (messageType != null && !SupervisorToNodeMessageType.start_round.toString().equals(messageType)) {
            throw new IllegalArgumentException("Invalid messageType for StartRoundMsg: " + messageType);
        }
        this.header = new StartRoundHeader();
    }

    // Getters
    public StartRoundHeader getHeader() {
        return header;
    }

    @JsonProperty("direction")
    public String getDirection() {
        return header.direction().toString();
    }

    @JsonProperty("messageType")
    public String getMessageType() {
        return header.messageType().toString();
    }

    // encode
    public String encode() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(this);
    }

    // decode
    public static StartRoundMsg decodeMessage(String jsonString) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(jsonString, StartRoundMsg.class);
    }

}
