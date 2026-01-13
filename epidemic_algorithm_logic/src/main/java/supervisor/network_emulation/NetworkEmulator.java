package supervisor.network_emulation;

import epidemic_core.node.Node;
import epidemic_core.node.mode.NodeMode;
import epidemic_core.node.mode.pull.anti_entropy.AntiEntropyPullNode;
import epidemic_core.node.mode.pull.gossip.GossipPullNode;
import epidemic_core.node.mode.pull.gossip.blind.coin.BlindCoinPullNode;
import epidemic_core.node.mode.pull.gossip.feedback.coin.FeedbackCoinPullNode;
import epidemic_core.node.mode.push.anti_entropy.AntiEntropyPushNode;
import epidemic_core.node.mode.push.gossip.GossipPushNode;
import epidemic_core.node.mode.push.gossip.blind.coin.BlindCoinPushNode;
import epidemic_core.node.mode.push.gossip.feedback.coin.FeedbackCoinPushNode;
import epidemic_core.node.mode.pushpull.anti_entropy.AntiEntropyPushPullNode;
import epidemic_core.node.mode.pushpull.gossip.GossipPushPullNode;
import epidemic_core.node.mode.pushpull.gossip.blind.coin.BlindCoinPushPullNode;
import epidemic_core.node.mode.pushpull.gossip.feedback.coin.FeedbackCoinPushPullNode;
import general.communication.utils.Address;
import supervisor.network_emulation.neighbors_and_subject.NetworkStructureManager;
import supervisor.network_emulation.topology_creation.Topology;
import supervisor.network_emulation.topology_creation.TopologyType;
import supervisor.network_emulation.utils.NodeIdToAddressTable;

import java.util.*;

public class NetworkEmulator {

    private final Address supervisorAddr = new Address("127.0.0.0", 7000);
    private final double pushInterval = 2;
    private final double defaultK = 2.0; // Default k value for gossip
    private Integer N;
    private Integer sourceNodes;
    private String topologyType;
    private String protocolType;
    private String modeType;
    private NetworkStructureManager networkStructureManager;
    
    private Map<Integer, Object> nodes; // Map<nodeId, Node>
    private Map<Integer, Thread> nodeThreads; // Map<nodeId, Thread>

    // Constructor
    public NetworkEmulator(Integer N,
                           Integer sourceNodes,
                           String topologyType,
                           String protocolType,
                           String modeType) {
        this.N = N;
        this.sourceNodes = sourceNodes;
        this.topologyType = topologyType;
        this.protocolType = protocolType;
        this.modeType = modeType;
        this.nodes = new HashMap<>();
        this.nodeThreads = new HashMap<>();
    }

    // Initialize the network
    public void initializeNetwork()
    {
        NodeIdToAddressTable infoTable = new NodeIdToAddressTable(N); // ip + port

        // ========== Create Topology ==========
        TopologyType type = TopologyType.fromString(topologyType);
        Topology topology = new Topology();
        Map<Integer, List<Integer>> adjMap = topology.createTopology(type, N);

        // ========== Network Structure Management ==========
        networkStructureManager = new NetworkStructureManager(adjMap, sourceNodes, N);

        NodeMode mode = NodeMode.fromString(modeType); // dissemination mode

        // ========== RUN ===========
        for(int id = 0; id < N; id++){
            List<Integer> neighbours = networkStructureManager.getNeighbors(id);
            String subjectStr = networkStructureManager.getSubjectForNode(id);

            Thread nodeThread = runMode(id, neighbours, subjectStr, infoTable, mode);
            nodeThreads.put(id, nodeThread);
        }
    }

    // Run the thread for each node
    public Thread runMode(Integer id,
                          List<Integer> neighbours,
                          String assignedSubjectAsSource,
                          NodeIdToAddressTable nodeIdToAddressTable,
                          NodeMode mode)
    {
        Node node = createNode(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, mode);

        nodes.put(id, node); // to store references to the nodes (for stopping them later)

        Thread t = Thread.startVirtualThread(() -> {
            if (node instanceof AntiEntropyPullNode n) n.startRunning();
            else if (node instanceof AntiEntropyPushNode n) n.startRunning();
            else if (node instanceof AntiEntropyPushPullNode n) n.startRunning();
            else if (node instanceof GossipPullNode n) n.startRunning();
            else if (node instanceof GossipPushNode n) n.startRunning();
            else if (node instanceof GossipPushPullNode n) n.startRunning();
        });

        t.setName("Node-" + id);
        return t;
    }

    // Create a node based on the mode and protocol
    private Node createNode(Integer id,
                            List<Integer> neighbours,
                            String assignedSubjectAsSource,
                            NodeIdToAddressTable nodeIdToAddressTable,
                            NodeMode mode) {
        
        String protocol = protocolType != null ? protocolType.toLowerCase() : "anti_entropy";
        Map<Integer, Address> addressTable = nodeIdToAddressTable.getAll();
        
        switch (mode) {
            case PULL:
                return switch (protocol) {
                    case "anti_entropy" -> new AntiEntropyPullNode(id, neighbours, assignedSubjectAsSource, addressTable, null, supervisorAddr);
                    case "gossip_feedback_coin", "feedback_coin" -> new FeedbackCoinPullNode(id, neighbours, assignedSubjectAsSource, addressTable, null, supervisorAddr, defaultK);
                    case "gossip_blind_coin", "blind_coin" -> new BlindCoinPullNode(id, neighbours, assignedSubjectAsSource, addressTable, null, supervisorAddr, defaultK);
                    default -> throw new IllegalArgumentException("Invalid protocol for PULL mode: " + protocol);
                };
            
            case PUSH:
                return switch (protocol) {
                    case "anti_entropy" -> new AntiEntropyPushNode(id, neighbours, assignedSubjectAsSource, addressTable, null, supervisorAddr);
                    case "gossip_feedback_coin", "feedback_coin" -> new FeedbackCoinPushNode(id, neighbours, assignedSubjectAsSource, addressTable, null, supervisorAddr, defaultK);
                    case "gossip_blind_coin", "blind_coin" -> new BlindCoinPushNode(id, neighbours, assignedSubjectAsSource, addressTable, null, supervisorAddr, defaultK);
                    default -> throw new IllegalArgumentException("Invalid protocol for PUSH mode: " + protocol);
                };
            
            case PUSHPULL:
                return switch (protocol) {
                    case "anti_entropy" -> new AntiEntropyPushPullNode(id, neighbours, assignedSubjectAsSource, addressTable, null, supervisorAddr);
                    case "gossip_feedback_coin", "feedback_coin" -> new FeedbackCoinPushPullNode(id, neighbours, assignedSubjectAsSource, addressTable, null, supervisorAddr, defaultK);
                    case "gossip_blind_coin", "blind_coin" -> new BlindCoinPushPullNode(id, neighbours, assignedSubjectAsSource, addressTable, null, supervisorAddr, defaultK);
                    default -> throw new IllegalArgumentException("Invalid protocol for PUSHPULL mode: " + protocol);
                };
            
            default:
                throw new IllegalArgumentException("Invalid mode: " + mode);
        }
    }
    
    // Stop all nodes
    public void stopNetwork() {
        for (Map.Entry<Integer, Thread> entry : nodeThreads.entrySet()) {
            Thread nodeThread = entry.getValue();
            if (nodeThread != null) {
                nodeThread.interrupt();
            }
        }
    }

    public Address getSupervisorAddr() {
        return supervisorAddr;
    }
}
