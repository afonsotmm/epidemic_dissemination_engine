package epidemic_core.message.ui_to_supervisor.start_round;

import general.communication.utils.Address;

public class StartRoundMsg {
    private final StartRoundHeader header;
    private Address ip;
    private Integer N;
    private Integer sourceNodes;
    private String topology;
    private String mode;

    // Constructor
    public StartRoundMsg(Address ip, Integer N, Integer sourceNodes, String topology, String mode) {
        this.header = new StartRoundHeader();
        this.ip = ip;
        this.N = N;
        this.sourceNodes = sourceNodes;
        this.topology = topology;
        this.mode = mode;
    }

    public String encode() {
        return  header.direction().toString() + ";"
                + header.messageType().toString() + ";"
                + ip + ";"
                + N + ";"
                + sourceNodes + ";"
                + topology + ";"
                + mode;
    }

    public static StartRoundMsg decodeMessage(String message) {
        String[] parts = message.split(";");
        return new StartRoundMsg(Address.parse(parts[2]), Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), parts[5], parts[6]);
    }

    // Getters
    public Address getIp() { return ip;}
    public Integer getN() { return N; }
    public Integer getSourceNodes() { return sourceNodes; }
    public String getTopology() { return topology; }
    public String getMode() { return mode; }
}
