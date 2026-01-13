package supervisor.network_emulation;

import epidemic_core.node.mode.NodeMode;
import epidemic_core.node.mode.pull.PullNode;
import epidemic_core.node.mode.push.PushNode;
import epidemic_core.node.mode.pushpull.PushPullNode;
import general.communication.utils.Address;
import supervisor.network_emulation.neighbors_and_subject.NetworkStructureManager;
import supervisor.network_emulation.neighbors_and_subject.NodeStructuralInfo;
import supervisor.network_emulation.neighbors_and_subject.StructuralInfosMatrix;
import supervisor.network_emulation.topology_creation.Topology;
import supervisor.network_emulation.topology_creation.TopologyType;
import supervisor.network_emulation.utils.NodeIdToAddressTable;

import java.util.*;

public class NetworkEmulator {

    private final Address supervisorAddr = new Address("127.0.0.0", 7000);
    private final double pushInterval = 2;
    private Integer N;
    private Integer sourceNodes;
    private String topologyType;
    private String modeType;
    private NetworkStructureManager networkStructureManager;
    
    private Map<Integer, Object> nodes; // Map<nodeId, Node>
    private Map<Integer, Thread> nodeThreads; // Map<nodeId, Thread>

    public NetworkEmulator(Integer N, Integer sourceNodes, String topologyType, String modeType) {
        this.N = N;
        this.sourceNodes = sourceNodes;
        this.topologyType = topologyType;
        this.modeType = modeType;
        this.nodes = new HashMap<>();
        this.nodeThreads = new HashMap<>();
    }

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

    public Thread runMode(Integer id,
                           List<Integer> neighbours,
                           String assignedSubjectAsSource,
                           NodeIdToAddressTable nodeIdToAddressTable,
                           NodeMode mode)
    {
        Object node;

        switch (mode) {
            case PULL -> node = new PullNode(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable.getAll(), null, supervisorAddr);
            //case PUSH -> node = new PushNode(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable.getAll(), null, supervisorAddr);
            //case PUSHPULL -> node = new PushPullNode(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable.getAll(), null, supervisorAddr);
            default -> throw new IllegalArgumentException("Invalid mode: " + mode);
        }

        // Guarda referência ao nó
        nodes.put(id, node);

        Thread t = Thread.startVirtualThread(() -> {
//            if (node instanceof PullNode pn) pn.startRunning();
//            else if (node instanceof PushNode ps) ps.startRunning();
//            else if (node instanceof PushPullNode pp) pp.startRunning();
            if (node instanceof PushNode ps) ps.startRunning();
        });

        t.setName("Node-" + id);
        return t;
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
