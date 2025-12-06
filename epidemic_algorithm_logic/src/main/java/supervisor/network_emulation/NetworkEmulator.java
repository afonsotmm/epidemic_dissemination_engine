package supervisor.network_emulation;

import epidemic_core.node.mode.NodeMode;
import epidemic_core.node.mode.pull.PullNode;
import epidemic_core.node.mode.push.PushNode;
import epidemic_core.node.mode.pushpull.PushPullNode;
import general.communication.utils.Address;
import supervisor.network_emulation.topology_creation.Topology;
import supervisor.network_emulation.topology_creation.TopologyType;
import supervisor.network_emulation.utils.NodeIdToAddressTable;
import supervisor.network_emulation.utils.Subjects;

import java.util.*;

public class NetworkEmulator {

    private final Address supervisorAddr = new Address("127.0.0.0", 7000);
    private final double pushInterval = 2;

    public void initializeNetwork(int N, int sourceNodes, String topologyType, String modeType)
    {
        NodeIdToAddressTable infoTable = new NodeIdToAddressTable(N); // ip + port

        // ========== Create Topology ==========
        TopologyType type = TopologyType.fromString(topologyType);
        Topology topology = new Topology();
        Map<Integer, List<Integer>> adjMap = topology.createTopology(type, N);

        NodeMode mode = NodeMode.fromString(modeType); // dissemination mode

        // ========== assigned subjects ==========
        Subjects[] allSubjects = Subjects.values();    // subjects possibilities

            // ===== source nodes selection =====
        sourceNodes = Math.min(sourceNodes, N);        // safety: sourceNodes <= N
        Set<Integer> sourceNodesId = new HashSet<>();  // doesnt allow repetition

        while (sourceNodesId.size() < sourceNodes) {
            Random random = new Random();
            int num = random.nextInt(N);
            sourceNodesId.add(num);
        }

        // ========== RUN ===========
        Random random = new Random();

        for(int id = 0; id < N; id++){
            List<Integer> neighbours = adjMap.get(id);
            String subjectStr;

            if(sourceNodesId.contains(id)){ // is source node
                Subjects subject = allSubjects[random.nextInt(allSubjects.length)];
                subjectStr = subject.name();
            }
            else{ subjectStr = null; } // doesnt have a subject

            Thread t = runMode(id, neighbours, subjectStr, infoTable, mode);
            t.start();
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
            case PULL -> node = new PullNode(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, supervisorAddr);
            case PUSH -> node = new PushNode(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, pushInterval, supervisorAddr);
            case PUSHPULL -> node = new PushPullNode(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, supervisorAddr);
            default -> throw new IllegalArgumentException("Invalid mode: " + mode);
        }

        Thread t = new Thread(() -> {
//            if (node instanceof PullNode pn) pn.startRunning();
//            else if (node instanceof PushNode ps) ps.startRunning();
//            else if (node instanceof PushPullNode pp) pp.startRunning();
            if (node instanceof PushNode ps) ps.startRunning();
        });

        t.setName("Node-" + id);
        return t;
    }

    public Address getSupervisorAddr() {
        return supervisorAddr;
    }
}
