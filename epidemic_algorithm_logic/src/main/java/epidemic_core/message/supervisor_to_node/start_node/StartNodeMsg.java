package epidemic_core.message.supervisor_to_node.start_node;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import epidemic_core.message.common.Direction;
import epidemic_core.message.common.MessageTopic;
import epidemic_core.message.supervisor_to_node.SupervisorToNodeMessageType;
import general.communication.utils.Address;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Message Supervisor -> Node: Initialize node with all configuration parameters
 * Sent via TCP when supervisor discovers node in distributed mode or creates it in local mode
 */
@JsonPropertyOrder({"direction", "messageType", "nodeId", "supervisorTcpAddress", "neighbors", 
                   "nodeToAddressTable", "subscribedTopics", "assignedSubjectAsSource", 
                   "mode", "protocol", "k"})
public class StartNodeMsg {

    private final StartNodeHeader header;
    private final int nodeId;
    private final String supervisorTcpAddress; // IP:port of supervisor's TCP server
    private final List<Integer> neighbors;
    private final Map<String, String> nodeToAddressTable; // Map<nodeId as string, "ip:port">
    private final List<Map<String, Object>> subscribedTopics; // List of {subject, sourceId}
    private final String assignedSubjectAsSource; // null if not a source node
    private final String mode; // "push", "pull", "pushpull"
    private final String protocol; // "anti_entropy", "blind_coin", "feedback_coin"
    private final Double k; // For gossip protocols (null if not gossip)

    @JsonCreator
    public StartNodeMsg(@JsonProperty("direction") String direction,
                       @JsonProperty("messageType") String messageType,
                       @JsonProperty("nodeId") int nodeId,
                       @JsonProperty("supervisorTcpAddress") String supervisorTcpAddress,
                       @JsonProperty("neighbors") List<Integer> neighbors,
                       @JsonProperty("nodeToAddressTable") Map<String, String> nodeToAddressTable,
                       @JsonProperty("subscribedTopics") List<Map<String, Object>> subscribedTopics,
                       @JsonProperty("assignedSubjectAsSource") String assignedSubjectAsSource,
                       @JsonProperty("mode") String mode,
                       @JsonProperty("protocol") String protocol,
                       @JsonProperty("k") Double k) {
        
        if (direction != null && !Direction.supervisor_to_node.toString().equals(direction)) {
            throw new IllegalArgumentException("Invalid direction for StartNodeMsg: " + direction);
        }
        if (messageType != null && !SupervisorToNodeMessageType.start_node.toString().equals(messageType)) {
            throw new IllegalArgumentException("Invalid messageType for StartNodeMsg: " + messageType);
        }
        this.header = new StartNodeHeader();
        this.nodeId = nodeId;
        this.supervisorTcpAddress = supervisorTcpAddress;
        this.neighbors = neighbors != null ? new ArrayList<>(neighbors) : new ArrayList<>();
        this.nodeToAddressTable = nodeToAddressTable != null ? new HashMap<>(nodeToAddressTable) : new HashMap<>();
        this.subscribedTopics = subscribedTopics != null ? new ArrayList<>(subscribedTopics) : new ArrayList<>();
        this.assignedSubjectAsSource = assignedSubjectAsSource;
        this.mode = mode;
        this.protocol = protocol;
        this.k = k;
    }

    // Getters
    public StartNodeHeader getHeader() { return header; }

    @JsonProperty("direction")
    public String getDirection() {
        return header.direction().toString();
    }

    @JsonProperty("messageType")
    public String getMessageType() {
        return header.messageType().toString();
    }

    @JsonProperty("nodeId")
    public int getNodeId() { return nodeId; }

    @JsonProperty("supervisorTcpAddress")
    public String getSupervisorTcpAddress() { return supervisorTcpAddress; }

    // Helper to parse supervisor TCP address string to Address object
    @JsonIgnore // Don't serialize this - it's a helper method, not a JSON field
    public Address getSupervisorTcpAddressAsAddress() {
        return supervisorTcpAddress != null ? Address.parse(supervisorTcpAddress) : null;
    }

    @JsonProperty("neighbors")
    public List<Integer> getNeighbors() { return new ArrayList<>(neighbors); }

    @JsonProperty("nodeToAddressTable")
    public Map<String, String> getNodeToAddressTable() { return new HashMap<>(nodeToAddressTable); }

    // Helper to convert nodeToAddressTable to Map<Integer, Address>
    @JsonIgnore // Don't serialize this - it's a helper method, not a JSON field
    public Map<Integer, Address> getNodeToAddressTableAsMap() {
        Map<Integer, Address> result = new HashMap<>();
        for (Map.Entry<String, String> entry : nodeToAddressTable.entrySet()) {
            int nodeId = Integer.parseInt(entry.getKey());
            Address address = Address.parse(entry.getValue());
            result.put(nodeId, address);
        }
        return result;
    }

    @JsonProperty("subscribedTopics")
    public List<Map<String, Object>> getSubscribedTopics() { return new ArrayList<>(subscribedTopics); }

    // Helper to convert subscribedTopics to List<MessageTopic>
    @JsonIgnore // Don't serialize this - it's a helper method, not a JSON field
    public List<MessageTopic> getSubscribedTopicsAsList() {
        List<MessageTopic> result = new ArrayList<>();
        for (Map<String, Object> topicMap : subscribedTopics) {
            String subject = (String) topicMap.get("subject");
            Integer sourceId = ((Number) topicMap.get("sourceId")).intValue();
            result.add(new MessageTopic(subject, sourceId));
        }
        return result;
    }

    @JsonProperty("assignedSubjectAsSource")
    public String getAssignedSubjectAsSource() { return assignedSubjectAsSource; }

    @JsonProperty("mode")
    public String getMode() { return mode; }

    @JsonProperty("protocol")
    public String getProtocol() { return protocol; }

    @JsonProperty("k")
    public Double getK() { return k; }

    public String encode() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(this);
    }

    public static StartNodeMsg decodeMessage(String jsonString) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(jsonString, StartNodeMsg.class);
    }
}
