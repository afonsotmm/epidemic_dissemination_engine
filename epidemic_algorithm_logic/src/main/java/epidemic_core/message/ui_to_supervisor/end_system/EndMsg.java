package epidemic_core.message.ui_to_supervisor.end_system;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import epidemic_core.message.common.Direction;
import epidemic_core.message.ui_to_supervisor.UiToSupervisorMessageType;
import java.io.IOException;

/**
 * Message UI -> Supervisor: End when canceled by user
 */
@JsonPropertyOrder({"direction", "messageType"})
public class EndMsg {

    private final EndHeader header;

    // Constructor
    @JsonCreator
    public EndMsg(@JsonProperty("direction") String direction,
                  @JsonProperty("messageType") String messageType){
        
        if (direction != null && !Direction.ui_to_supervisor.toString().equals(direction)) {
            throw new IllegalArgumentException("Invalid direction for EndRoundMsg: " + direction);
        }
        if (messageType != null && !UiToSupervisorMessageType.end_system.toString().equals(messageType)) {
            throw new IllegalArgumentException("Invalid messageType for EndRoundMsg: " + messageType);
        }
        this.header = new EndHeader();
    }

    // Getters
    public EndHeader getHeader() { return header; }

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
    public static EndMsg decodeMessage(String jsonString) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(jsonString, EndMsg.class);
    }
}
