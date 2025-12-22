package epidemic_core.message.FromUI;

import epidemic_core.message.FromNode.StatusMessage;
import general.communication.utils.Address;

public class StartMessage {
    private Address ip;
    private Integer N;
    private Integer sourceNodes;
    private String topology;
    private String mode;

    // Constructor
    public StartMessage(Address ip, Integer N, Integer sourceNodes, String topology, String mode) {
        this.ip = ip;
        this.N = N;
        this.sourceNodes = sourceNodes;
        this.topology = topology;
        this.mode = mode;
    }

    public static StartMessage decodeMessage(String message) {
        String[] parts = message.split(";");
        return new StartMessage(Address.parse(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), parts[4], parts[5]);
    }

    // Getters
    public Address getIp() { return ip;}
    public Integer getN() { return N; }
    public Integer getSourceNodes() { return sourceNodes; }
    public String getTopology() { return topology; }
    public String getMode() { return mode; }
}
