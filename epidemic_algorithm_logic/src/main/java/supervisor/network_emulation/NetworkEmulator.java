package supervisor.network_emulation;

import epidemic_core.node.DistributedNodeStub;
import epidemic_core.message.common.MessageTopic;
import general.communication.utils.Address;
import supervisor.network_emulation.neighbors_and_subject.NetworkStructureManager;
import supervisor.network_emulation.topology_creation.Topology;
import supervisor.network_emulation.topology_creation.TopologyType;
import supervisor.network_emulation.utils.NodeIdToAddressTable;

import java.util.*;
import java.util.concurrent.CountDownLatch;

public class NetworkEmulator {

    private final Address supervisorAddr;
    private final double defaultK = 2.0; // Default k value for gossip
    private Integer N;
    private Integer sourceNodes;
    private String topologyType;
    private String protocolType;
    private String modeType;
    private NetworkStructureManager networkStructureManager;
    private NodeIdToAddressTable nodeIdToAddressTable;
    
    private Map<Integer, DistributedNodeStub> nodeStubs; // Map<nodeId, DistributedNodeStub> - nodes start in WAVING mode
    private Map<Integer, Thread> nodeThreads; // Map<nodeId, Thread>

    // Constructor
    public NetworkEmulator(Integer N,
                           Integer sourceNodes,
                           String topologyType,
                           String protocolType,
                           String modeType,
                           Address supervisorAddr) {
        this.N = N;
        this.sourceNodes = sourceNodes;
        this.topologyType = topologyType;
        this.protocolType = protocolType;
        this.modeType = modeType;
        this.supervisorAddr = supervisorAddr;
        this.nodeStubs = new HashMap<>();
        this.nodeThreads = new HashMap<>();
    }

    // Initialize the network
    public void initializeNetwork()
    {
        this.nodeIdToAddressTable = new NodeIdToAddressTable(N); // ip + port

        // ========== Create Topology ==========
        TopologyType type = TopologyType.fromString(topologyType);
        Topology topology = new Topology();
        Map<Integer, List<Integer>> adjMap = topology.createTopology(type, N);

        // ========== Network Structure Management ==========
        networkStructureManager = new NetworkStructureManager(adjMap, sourceNodes, N);

        // ========== RUN ==========
        // Create DistributedNodeStub for each node (they start in WAVING mode)
        // Nodes will send HelloMsg and wait for StartNodeMsg from supervisor
        // Use CountDownLatch to wait for all node stubs to be created
        CountDownLatch creationLatch = new CountDownLatch(N);
        
        for(int id = 0; id < N; id++){
            final int nodeId = id;
            Address nodeAddress = nodeIdToAddressTable.get(nodeId);
            
            // Create DistributedNodeStub in a separate virtual thread with delay to avoid socket conflicts
            Thread.startVirtualThread(() -> {
                try {
                    // Small delay to avoid socket creation conflicts
                    Thread.sleep(nodeId * 10); // 10ms delay between each node
                    
                    // Create DistributedNodeStub - node starts in WAVING mode
                    // It will send HelloMsg via UDP broadcast and wait for StartNodeMsg
                    DistributedNodeStub stub = new DistributedNodeStub(nodeAddress.getIp(), nodeAddress.getPort());
                    nodeStubs.put(nodeId, stub);
                    
                    creationLatch.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    System.err.println("Error creating DistributedNodeStub for node " + nodeId + ": " + e.getMessage());
                    e.printStackTrace();
                    creationLatch.countDown(); // Still count down to avoid blocking
                }
            });
        }
        
        // Wait for all node stubs to be created before returning
        try {
            creationLatch.await();
            System.out.println("All " + N + " node stubs created successfully (in WAVING mode)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted while waiting for node stubs to be created");
        }
    }
    
    /**
     * Generate subscribed topics based on all sources
     * Each node is interested in all subjects published by sources (subject + sourceId)
     */
    private List<MessageTopic> generateSubscribedTopics() {
        List<MessageTopic> topics = new ArrayList<>();
        Set<Integer> sourceNodesId = networkStructureManager.getSourceNodesId();
        
        for (Integer sourceId : sourceNodesId) {
            String subject = networkStructureManager.getSubjectForNode(sourceId);
            if (subject != null) {
                // Create MessageTopic with subject and sourceId
                topics.add(new MessageTopic(subject, sourceId));
            }
        }
        
        System.out.println("Generated " + topics.size() + " subscribed topics for all nodes");
        return topics;
    }

    // Note: Nodes are now created by DistributedNodeStub after receiving StartNodeMsg
    // The NetworkEmulator only creates the stubs in WAVING mode
    
    // Stop all node stubs
    public void stopNetwork() {
        for (Map.Entry<Integer, DistributedNodeStub> entry : nodeStubs.entrySet()) {
            DistributedNodeStub stub = entry.getValue();
            if (stub != null) {
                stub.stop();
            }
        }
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
    
    // Get all node addresses
    public Map<Integer, Address> getNodeAddresses() {
        return nodeIdToAddressTable != null ? nodeIdToAddressTable.getAll() : new HashMap<>();
    }

    // Get structural information matrix
    public supervisor.network_emulation.neighbors_and_subject.StructuralInfosMatrix getStructuralInfosMatrix() {
        return networkStructureManager != null ? networkStructureManager.getStructuralInfosMatrix() : null;
    }
    
    // Get NetworkStructureManager (for accessing source nodes info)
    public NetworkStructureManager getNetworkStructureManager() {
        return networkStructureManager;
    }
    
    /**
     * Get all node stubs map (for direct access)
     */
    public Map<Integer, DistributedNodeStub> getNodeStubs() {
        return nodeStubs;
    }
}
