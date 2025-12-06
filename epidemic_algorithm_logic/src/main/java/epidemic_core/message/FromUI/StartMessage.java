package epidemic_core.message.FromUI;

import epidemic_core.message.FromNode.StatusMessage;

public class StartMessage {
    private Integer N;
    private Integer sourceNodes;
    private String topology;
    private String mode;

    // Constructor
    public StartMessage(Integer N, Integer sourceNodes, String topology, String mode) {
        this.N = N;
        this.sourceNodes = sourceNodes;
        this.topology = topology;
        this.mode = mode;
    }

    public static StartMessage decodeMessage(String message) {
        String[] parts = message.split(";");
        return new StartMessage(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), parts[3], parts[4]);
    }

    // Getters
    public Integer getN() { return N; }
    public Integer getSourceNodes() { return sourceNodes; }
    public String getTopology() { return topology; }
    public String getMode() { return mode; }
}
