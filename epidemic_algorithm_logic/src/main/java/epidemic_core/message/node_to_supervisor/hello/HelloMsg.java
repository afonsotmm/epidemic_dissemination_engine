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

@JsonPropertyOrder({"direction", "messageType", "tcpAddress", "udpAddress"})
@JsonIgnoreProperties(ignoreUnknown = true)
public class HelloMsg {

    private final HelloHeader header;
    private final String tcpAddress;
    private final String udpAddress;

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

    @Deprecated
    @JsonIgnore
    public String getAddress() { return tcpAddress; }

    public Address getTcpAddressAsAddress() {
        return tcpAddress != null ? Address.parse(tcpAddress) : null;
    }

    @JsonIgnore
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
