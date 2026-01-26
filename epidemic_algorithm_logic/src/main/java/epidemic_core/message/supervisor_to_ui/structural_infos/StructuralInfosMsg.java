package epidemic_core.message.supervisor_to_ui.structural_infos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import epidemic_core.message.common.Direction;
import epidemic_core.message.supervisor_to_ui.SupervisorToUiMessageType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Message Supervisor -> UI: Neighbors and subject for each node (json)
 */

public class StructuralInfosMsg {
    
    @JsonIgnore
    private final StructuralInfosHeader header;
    
    @JsonProperty("nodes")
    private final List<NodeInfo> nodes;

    public static class NodeInfo {
        private final Integer id;
        private final List<Integer> neighbors;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final String subject;

        // Constructor
        @JsonCreator
        public NodeInfo(@JsonProperty("id") Integer id, 
                        @JsonProperty("neighbors") List<Integer> neighbors, 
                        @JsonProperty("subject") String subject)
        {
            this.id = id;
            this.neighbors = neighbors;
            this.subject = subject;
        }

        // Getters
        @JsonProperty("id")
        public Integer getId() { return id; }
        
        @JsonProperty("neighbors")
        public List<Integer> getNeighbors() { return neighbors; }
        
        @JsonProperty("subject")
        public String getSubject() { return subject; }
    }

    // Constructor
    @JsonCreator
    public StructuralInfosMsg(@JsonProperty("direction") String direction,
                              @JsonProperty("messageType") String messageType,
                              @JsonProperty("nodes") List<NodeInfo> nodes )
    {
        if (direction != null && !Direction.supervisor_to_ui.toString().equals(direction)) {
            throw new IllegalArgumentException("Invalid direction for StructuralInfosMsg: " + direction);
        }
        if (messageType != null && !SupervisorToUiMessageType.structural_infos.toString().equals(messageType)) {
            throw new IllegalArgumentException("Invalid messageType for StructuralInfosMsg: " + messageType);
        }
        this.header = new StructuralInfosHeader();
        this.nodes = nodes;
    }

    // Msg: converts a StructuralInfosMatrix into a list of structural infos per node
    public static StructuralInfosMsg fromStructuralInfosMatrix(supervisor.network_emulation.neighbors_and_subject.StructuralInfosMatrix matrix)
    {
        List<NodeInfo> nodesList = new ArrayList<>();
        
        Map<Integer, List<Integer>> adjMap = matrix.adjMap();
        Map<Integer, String> nodeIdToSubject = matrix.nodeIdToSubject();
        
        // adjMap and nodeIdToSubject in a array for each node
        for (Integer nodeId : adjMap.keySet()) {
            List<Integer> neighbors = adjMap.get(nodeId);
            String subject = nodeIdToSubject.get(nodeId);
            
            NodeInfo nodeInfo = new NodeInfo(nodeId, neighbors, subject);
            nodesList.add(nodeInfo);
        }

        return new StructuralInfosMsg(
            Direction.supervisor_to_ui.toString(),
            SupervisorToUiMessageType.structural_infos.toString(),
            nodesList
        );
    }

    public String encode() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(this);
    }

    public static StructuralInfosMsg decodeMessage(String jsonString) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(jsonString, StructuralInfosMsg.class);
    }

    // Getters
    public StructuralInfosHeader getHeader() {return header; }
    public List<NodeInfo> getNodes() { return nodes; }

    @JsonProperty("direction")
    public String getDirection() { return header.direction().toString(); }

    @JsonProperty("messageType")
    public String getMessageType() { return header.messageType().toString();}
}
