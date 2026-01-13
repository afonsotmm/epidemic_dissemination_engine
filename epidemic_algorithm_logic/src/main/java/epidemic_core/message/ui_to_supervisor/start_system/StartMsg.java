package epidemic_core.message.ui_to_supervisor.start_system;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import epidemic_core.message.common.Direction;
import epidemic_core.message.ui_to_supervisor.UiToSupervisorMessageType;
import general.communication.utils.Address;
import java.io.IOException;

/**
 * Message UI -> Supervisor: All infos selected by the user (json)
 */

public class StartMsg {
    private final StartHeader header;
    private Address addr;
    private Integer N;
    private Integer sourceNodes;
    private String topology;
    private String protocol;
    private String mode;

    // Constructor
    @JsonCreator
    private StartMsg(@JsonProperty("direction") String direction,
                     @JsonProperty("messageType") String messageType,
                     @JsonProperty("addr") String addrString,
                     @JsonProperty("N") Integer N,
                     @JsonProperty("sourceNodes") Integer sourceNodes,
                     @JsonProperty("topology") String topology,
                     @JsonProperty("protocol") String protocol,
                     @JsonProperty("mode") String mode) {
         
        if (direction != null && !Direction.ui_to_supervisor.toString().equals(direction)) {
            throw new IllegalArgumentException("Invalid direction for StartRoundMsg: " + direction);
        }
        if (messageType != null && !UiToSupervisorMessageType.start_system.toString().equals(messageType)) {
            throw new IllegalArgumentException("Invalid messageType for StartRoundMsg: " + messageType);
        }
        this.header = new StartHeader();
        this.addr = addrString != null ? Address.parse(addrString) : null;
        this.N = N;
        this.sourceNodes = sourceNodes;
        this.topology = topology;
        this.protocol = protocol;
        this.mode = mode;
    }

    public static StartMsg decodeMessage(String jsonString) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        StartMsg msg = objectMapper.readValue(jsonString, StartMsg.class);
        return msg;
    }

    // Getters
    public StartHeader getHeader() { return header; }
    public Address getAddr() { return addr;}
    public Integer getN() { return N; }
    public Integer getSourceNodes() { return sourceNodes; }
    public String getTopology() { return topology; }
    public String getProtocol() { return protocol; }
    public String getMode() { return mode; }

    @JsonProperty("direction")
    public String getDirection() {
        return header.direction().toString();
    }

    @JsonProperty("messageType")
    public String getMessageType() {
        return header.messageType().toString();
    }

    @JsonProperty("addr")
    private String getAddrAsString() {
        return addr != null ? addr.getIp() + ":" + addr.getPort() : null;
    }
}
