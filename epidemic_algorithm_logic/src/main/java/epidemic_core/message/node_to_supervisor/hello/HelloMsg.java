package epidemic_core.message.node_to_supervisor.hello;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import epidemic_core.message.common.Direction;
import epidemic_core.message.node_to_supervisor.NodeToSupervisorMessageType;
import general.communication.utils.Address;

import java.io.IOException;

/**
 * Message Node -> Supervisor: Node announces its presence (for distributed deployment)
 * Sent via UDP broadcast periodically when node is in "Waving" mode
 */
@JsonPropertyOrder({"direction", "messageType", "tcpAddress", "udpAddress"})
@JsonIgnoreProperties(ignoreUnknown = true) // Ignore unknown properties like "address" for backward compatibility
public class HelloMsg {

    private final HelloHeader header;
    private final String tcpAddress; // TCP IP:port as string (for supervisor-to-node messages)
    private final String udpAddress; // UDP IP:port as string (for node-to-node and StartRoundMsg)

    @JsonCreator
    public HelloMsg(@JsonProperty("direction") String direction,
                   @JsonProperty("messageType") String messageType,
                   @JsonProperty("tcpAddress") String tcpAddress,
                   @JsonProperty("udpAddress") String udpAddress) {
        
        if (direction != null && !Direction.node_to_supervisor.toString().equals(direction)) {
            throw new IllegalArgumentException("Invalid direction for HelloMsg: " + direction);
        }
        if (messageType != null && !NodeToSupervisorMessageType.hello.toString().equals(messageType)) {
            throw new IllegalArgumentException("Invalid messageType for HelloMsg: " + messageType);
        }
        this.header = new HelloHeader();
        this.tcpAddress = tcpAddress;
        this.udpAddress = udpAddress;
    }

    // Getters
    public HelloHeader getHeader() { return header; }

    @JsonProperty("direction")
    public String getDirection() {
        return header.direction().toString();
    }

    @JsonProperty("messageType")
    public String getMessageType() {
        return header.messageType().toString();
    }

    @JsonProperty("tcpAddress")
    public String getTcpAddress() { return tcpAddress; }
    
    @JsonProperty("udpAddress")
    public String getUdpAddress() { return udpAddress; }
    
    // Backward compatibility: return TCP address as "address" (for existing code)
    // Note: This method is not serialized (no @JsonProperty) to avoid duplicate "address" field in JSON
    @Deprecated
    @JsonIgnore // Don't serialize this - we only serialize tcpAddress and udpAddress
    public String getAddress() { return tcpAddress; }

    // Helper to parse TCP address string to Address object
    @JsonIgnore // Don't serialize this - it's a helper method, not a JSON field
    public Address getTcpAddressAsAddress() {
        return tcpAddress != null ? Address.parse(tcpAddress) : null;
    }
    
    // Helper to parse UDP address string to Address object
    @JsonIgnore // Don't serialize this - it's a helper method, not a JSON field
    public Address getUdpAddressAsAddress() {
        return udpAddress != null ? Address.parse(udpAddress) : null;
    }

    public String encode() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(this);
    }

    public static HelloMsg decodeMessage(String jsonString) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(jsonString, HelloMsg.class);
    }
}
