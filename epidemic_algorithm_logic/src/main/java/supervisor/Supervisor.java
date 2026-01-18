package supervisor;

import epidemic_core.message.common.MessageDispatcher;
import epidemic_core.message.common.MessageTopic;
import epidemic_core.message.node_to_supervisor.hello.HelloMsg;
import epidemic_core.message.supervisor_to_node.kill_node.KillNodeMsg;
import epidemic_core.message.supervisor_to_node.start_node.StartNodeMsg;
import epidemic_core.message.supervisor_to_node.start_round.StartRoundMsg;
import epidemic_core.message.supervisor_to_ui.structural_infos.StructuralInfosMsg;
import epidemic_core.message.ui_to_supervisor.end_system.EndMsg;
import epidemic_core.message.ui_to_supervisor.start_system.StartMsg;
import supervisor.network_emulation.neighbors_and_subject.NetworkStructureManager;
import supervisor.network_emulation.topology_creation.Topology;
import supervisor.network_emulation.topology_creation.TopologyType;
import supervisor.network_emulation.utils.NodeIdToAddressTable;
import general.communication.Communication;
import general.communication.implementation.TcpCommunication;
import general.communication.implementation.UdpCommunication;
import general.communication.utils.Address;
import supervisor.communication.Dispatcher;
import supervisor.communication.Listener;
import supervisor.communication.Worker;
import supervisor.network_emulation.NetworkEmulator;
import supervisor.ui.SupervisorGui;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Orchestrator responsible for managing communication infrastructure
 * and controlling network topology initialization and lifecycle
 */

public class Supervisor{

    private Communication nodeCommunication;  // UDP for nodes
    private Communication uiCommunication;    // TCP for external UI

    private Listener listener;
    private Dispatcher dispatcher;
    private Worker worker;
    private NetworkEmulator system;

    private BlockingQueue<String> udpMsgsQueue;  // Messages from UDP (nodes)
    private BlockingQueue<String> tcpMsgsQueue;  // Messages from TCP (UI)
    private BlockingQueue<String> nodeQueue;
    private BlockingQueue<String> uiQueue;

    private Thread listenerThread;
    private Thread dispatcherThread;
    private Thread workerThread;
    private Thread startRoundThread;

    private StartMsg startMessage;
    private volatile boolean isNetworkRunning = false;
    private final long roundInterval = 5;
    private int supervisorPort = 7000; // Default port, updated in initialize()
    private int supervisorTcpPort = 7001; // TCP port for node communication (fixed port)
    private SupervisorGui gui;
    private int currentRound = 0;
    private volatile boolean externalUiAvailable = true; // Track if external UI is available
    
    // Distributed deployment mode fields
    private TcpCommunication nodeTcpCommunication; // TCP server for nodes (distributed mode)
    private Map<String, Integer> addressToNodeId; // Map<"tcpAddress", nodeId> for discovered nodes (using TCP address as key)
    private Map<Integer, Address> discoveredNodeAddresses; // Map<nodeId, UDP Address> for discovered nodes (for StartRoundMsg)
    private Map<Integer, Address> discoveredNodeTcpAddresses; // Map<nodeId, TCP Address> for discovered nodes (for StartNodeMsg/KillNodeMsg)
    private volatile boolean isSearching = false; // Flag for SEARCHING state
    private Thread searchingThread; // Thread for searching mode
    private static final int SEARCHING_TIMEOUT_MS = 120000; // 2 minutes
    private volatile boolean isDistributedMode = false; // Flag to track deployment mode

    public Supervisor(){
        // buffers:
        this.udpMsgsQueue = new LinkedBlockingQueue<>();  // Messages from UDP (nodes)
        this.tcpMsgsQueue = new LinkedBlockingQueue<>();  // Messages from TCP (UI)
        this.nodeQueue = new LinkedBlockingQueue<>();
        this.uiQueue = new LinkedBlockingQueue<>();

        // Separate communications: UDP for nodes, TCP for UI
        this.nodeCommunication = new UdpCommunication();
        this.uiCommunication = new TcpCommunication();
        
        this.listener = new Listener(this, udpMsgsQueue, tcpMsgsQueue);
        this.dispatcher = new Dispatcher(udpMsgsQueue, tcpMsgsQueue, nodeQueue, uiQueue);
        this.worker = new Worker(this, nodeQueue, uiQueue);
    }

    public Communication getNodeCommunication() {
        return nodeCommunication;
    }

    public Communication getUiCommunication() {
        return uiCommunication;
    }
    
    /**
     * Get TCP communication for nodes (distributed mode) - may be null in local mode
     */
    public Communication getNodeTcpCommunication() {
        return nodeTcpCommunication;
    }

    public Address getUiAddress() {
        return startMessage != null ? startMessage.getAddr() : null;
    }

    // initialize topology and nodes
    public void startNetwork(StartMsg startMessage){
        this.startMessage = startMessage;
        
        // Initialize local GUI
        gui = new SupervisorGui(startMessage.getN());
        
        // Check deployment mode
        String deployment = startMessage.getDeployment();
        if (deployment == null || "local".equals(deployment)) {
            // LOCAL MODE: Create nodes locally (existing behavior)
            isDistributedMode = false;
            startLocalNetwork(startMessage);
        } else if ("distributed".equals(deployment)) {
            // DISTRIBUTED MODE: Discover nodes via HelloMsg
            isDistributedMode = true;
            startDistributedNetwork(startMessage);
        } else {
            System.err.println("Unknown deployment mode: " + deployment + ". Using local mode.");
            isDistributedMode = false;
            startLocalNetwork(startMessage);
        }
    }
    
    /**
     * Local mode: Create DistributedNodeStub locally, then enter SEARCHING mode
     * Nodes start in WAVING mode and send HelloMsg, just like distributed mode
     */
    private void startLocalNetwork(StartMsg startMessage) {
        // Get supervisor address (where it's listening)
        Address supervisorAddress = new Address("127.0.0.1", supervisorPort);
        system = new NetworkEmulator(startMessage.getN(), startMessage.getSourceNodes(), startMessage.getTopology(), startMessage.getProtocol(), startMessage.getMode(), supervisorAddress);
        
        // Initialize network (creates DistributedNodeStub for each node in WAVING mode)
        system.initializeNetwork();
        
        // Initialize TCP server for node communication (needed for StartNodeMsg and InfectionUpdateMsg/RemotionUpdateMsg)
        nodeTcpCommunication = new TcpCommunication();
        Address supervisorTcpAddress = new Address("127.0.0.1", supervisorTcpPort);
        nodeTcpCommunication.setupSocket(supervisorTcpAddress);
        System.out.println("[Supervisor] TCP server initialized for nodes on port " + supervisorTcpPort);
        
        // Start TCP listener thread for nodes
        listener.startNodeTcpListening();
        
        // Initialize address mapping (empty - will be filled by HelloMsg)
        addressToNodeId = new HashMap<>();
        discoveredNodeAddresses = new HashMap<>();
        discoveredNodeTcpAddresses = new HashMap<>();
        
        // Enter SEARCHING mode - same as distributed mode
        // Supervisor will discover nodes via HelloMsg, ignoring the addresses it created
        // The created addresses are only used to create the DistributedNodeStub instances
        isSearching = true;
        startSearchingMode(startMessage);
    }
    
    /**
     * Distributed mode: Discover nodes via HelloMsg and send StartNodeMsg
     */
    private void startDistributedNetwork(StartMsg startMessage) {
        System.out.println("[Supervisor] Starting distributed network discovery...");
        
        // Initialize TCP server for node communication
        nodeTcpCommunication = new TcpCommunication();
        Address supervisorTcpAddress = new Address("127.0.0.1", supervisorTcpPort);
        nodeTcpCommunication.setupSocket(supervisorTcpAddress);
        System.out.println("[Supervisor] TCP server initialized for nodes on port " + supervisorTcpPort);
        
        // Start TCP listener thread for nodes (distributed mode)
        listener.startNodeTcpListening();
        
        // Initialize address mapping
        addressToNodeId = new HashMap<>();
        discoveredNodeAddresses = new HashMap<>();
        discoveredNodeTcpAddresses = new HashMap<>();
        
        // Enter SEARCHING mode
        isSearching = true;
        startSearchingMode(startMessage);
    }
    
    /**
     * Register source nodes as infected in round 0 (like old supervisor)
     */
    private void registerSourceNodesInRound0() {
        if (system == null) return;
        
        // Get source nodes information from NetworkStructureManager
        supervisor.network_emulation.neighbors_and_subject.NetworkStructureManager nsm = 
            system.getNetworkStructureManager();
        if (nsm == null) return;
        
        Set<Integer> sourceNodesId = nsm.getSourceNodesId();
        for (Integer nodeId : sourceNodesId) {
            String subject = nsm.getSubjectForNode(nodeId);
            if (subject != null) {
                // Register source node as infected in round 0 (explicit round parameter)
                gui.recordInfection(nodeId, nodeId, subject, nodeId, 0, "SOURCE", 0);
                System.out.println("Registered SOURCE node " + nodeId + " with subject '" + subject + "' in round 0");
            }
        }
    }
    
    /**
     * Start SEARCHING mode: Listen for HelloMsg and map addresses to Node IDs
     */
    private void startSearchingMode(StartMsg startMessage) {
        System.out.println("[Supervisor] Entering SEARCHING mode - listening for HelloMsg from nodes...");
        
        int targetN = startMessage.getN();
        long startTime = System.currentTimeMillis();
        
        searchingThread = Thread.startVirtualThread(() -> {
            while (isSearching && addressToNodeId.size() < targetN) {
                // Check timeout (2 minutes)
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed >= SEARCHING_TIMEOUT_MS) {
                    System.out.println("[Supervisor] SEARCHING timeout reached (2 minutes). Found " + addressToNodeId.size() + " nodes.");
                    break;
                }
                
                // Check if we have all nodes
                if (addressToNodeId.size() >= targetN) {
                    System.out.println("[Supervisor] All " + targetN + " nodes discovered!");
                    break;
                }
                
                // Small sleep to avoid busy-waiting
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            // Exit SEARCHING mode and send StartNodeMsg to all discovered nodes
            isSearching = false;
            finishSearchingAndSendStartNodeMsg(startMessage);
        });
    }
    
    /**
     * Handle HelloMsg: Map TCP address to Node ID (incremental) and store both TCP and UDP addresses
     */
    public void handleHelloMsg(HelloMsg helloMsg) {
        if (!isSearching) {
            return; // Not in SEARCHING mode
        }
        
        String tcpAddress = helloMsg.getTcpAddress();
        String udpAddress = helloMsg.getUdpAddress();
        
        if (tcpAddress == null || addressToNodeId.containsKey(tcpAddress)) {
            return; // Already mapped or invalid address
        }
        
        // Assign incremental Node ID
        int nodeId = addressToNodeId.size();
        addressToNodeId.put(tcpAddress, nodeId); // Use TCP address as key
        
        // Store both TCP and UDP addresses
        Address tcpAddr = Address.parse(tcpAddress);
        Address udpAddr = udpAddress != null ? Address.parse(udpAddress) : null;
        
        if (udpAddr == null) {
            System.err.println("[Supervisor] Warning: HelloMsg missing UDP address for TCP address: " + tcpAddress);
            return;
        }
        
        discoveredNodeTcpAddresses.put(nodeId, tcpAddr);
        discoveredNodeAddresses.put(nodeId, udpAddr);
        
        // Record in GUI discovered nodes table (show both TCP and UDP addresses)
        if (gui != null) {
            gui.recordDiscoveredNode(nodeId, tcpAddress, udpAddress);
        }
        
        System.out.println("[Supervisor] Discovered node: TCP=" + tcpAddress + ", UDP=" + udpAddress + " -> Node ID: " + nodeId + " (Total: " + addressToNodeId.size() + "/" + (startMessage != null ? startMessage.getN() : "?") + ")");
    }
    
    /**
     * Finish SEARCHING mode: Calculate topology and send StartNodeMsg to all discovered nodes
     */
    private void finishSearchingAndSendStartNodeMsg(StartMsg startMessage) {
        System.out.println("[Supervisor] Exiting SEARCHING mode. Configuring " + addressToNodeId.size() + " nodes...");
        
        if (addressToNodeId.isEmpty()) {
            System.err.println("[Supervisor] No nodes discovered! Cannot start network.");
            return;
        }
        
        try {
            int N = addressToNodeId.size();
            int sourceNodes = Math.min(startMessage.getSourceNodes(), N);
            String topologyType = startMessage.getTopology();
            String protocol = startMessage.getProtocol();
            String mode = startMessage.getMode();
            
            // Get NetworkStructureManager - use existing one in local mode, create new in distributed mode
            NetworkStructureManager nsm;
            if (system != null && system.getNetworkStructureManager() != null) {
                // Local mode: use existing NetworkStructureManager from system
                nsm = system.getNetworkStructureManager();
                System.out.println("[Supervisor] Using existing NetworkStructureManager (local mode)");
            } else {
                // Distributed mode: create new NetworkStructureManager
                TopologyType type = TopologyType.fromString(topologyType);
                Topology topology = new Topology();
                Map<Integer, List<Integer>> adjMap = topology.createTopology(type, N);
                nsm = new NetworkStructureManager(adjMap, sourceNodes, N);
                System.out.println("[Supervisor] Created new NetworkStructureManager (distributed mode)");
            }
            
            // Create NodeIdToAddressTable from discovered addresses
            // HelloMsg contains both TCP and UDP addresses - use UDP addresses for node-to-node communication
            NodeIdToAddressTable nodeIdToAddressTable = new NodeIdToAddressTable(N);
            Map<Integer, Address> addressTable = nodeIdToAddressTable.getAll();
            
            // Map discovered UDP addresses to nodeToAddressTable (from HelloMsg)
            // discoveredNodeAddresses already contains UDP addresses from handleHelloMsg()
            for (Map.Entry<Integer, Address> entry : discoveredNodeAddresses.entrySet()) {
                int nodeId = entry.getKey();
                Address udpAddress = entry.getValue(); // UDP address from HelloMsg
                addressTable.put(nodeId, udpAddress); // nodeToAddressTable uses UDP addresses for node-to-node
            }
            
            // Generate subscribed topics (all nodes subscribe to all source topics)
            List<MessageTopic> subscribedTopics = generateSubscribedTopics(nsm);
            
            // Supervisor TCP address (for StartNodeMsg)
            Address supervisorTcpAddress = new Address("127.0.0.1", supervisorTcpPort);
            String supervisorTcpAddressStr = supervisorTcpAddress.getIp() + ":" + supervisorTcpAddress.getPort();
            
            // Calculate k value for gossip protocols
            Double k = null;
            if (protocol != null && (protocol.contains("coin") || protocol.contains("gossip"))) {
                k = 2.0; // Default k value
            }
            
            // Send StartNodeMsg to each discovered node via TCP
            // Use TCP addresses from HelloMsg (stored in discoveredNodeTcpAddresses)
            for (Map.Entry<Integer, Address> entry : discoveredNodeTcpAddresses.entrySet()) {
                int nodeId = entry.getKey();
                Address nodeTcpAddress = entry.getValue(); // TCP address from HelloMsg
                String nodeTcpAddressStr = nodeTcpAddress.getIp() + ":" + nodeTcpAddress.getPort();
                
                // Get node configuration
                List<Integer> neighbors = nsm.getNeighbors(nodeId);
                String assignedSubjectAsSource = nsm.getSubjectForNode(nodeId);
                
                // Convert nodeToAddressTable to Map<String, String>
                Map<String, String> nodeToAddressTableStr = new HashMap<>();
                for (Map.Entry<Integer, Address> addrEntry : addressTable.entrySet()) {
                    nodeToAddressTableStr.put(String.valueOf(addrEntry.getKey()), 
                                             addrEntry.getValue().getIp() + ":" + addrEntry.getValue().getPort());
                }
                
                // Convert subscribedTopics to List<Map<String, Object>>
                List<Map<String, Object>> subscribedTopicsJson = new ArrayList<>();
                for (MessageTopic topic : subscribedTopics) {
                    Map<String, Object> topicMap = new HashMap<>();
                    topicMap.put("subject", topic.subject());
                    topicMap.put("sourceId", topic.sourceId());
                    subscribedTopicsJson.add(topicMap);
                }
                
                // Create StartNodeMsg
                StartNodeMsg startNodeMsg = new StartNodeMsg(
                    epidemic_core.message.common.Direction.supervisor_to_node.toString(),
                    epidemic_core.message.supervisor_to_node.SupervisorToNodeMessageType.start_node.toString(),
                    nodeId,
                    supervisorTcpAddressStr,
                    neighbors,
                    nodeToAddressTableStr,
                    subscribedTopicsJson,
                    assignedSubjectAsSource,
                    mode,
                    protocol,
                    k
                );
                
                // Send via TCP to node (StartNodeMsg is large and contains nodeToAddressTable)
                // Nodes listen for StartNodeMsg via TCP on the same port as UDP (TCP and UDP can share ports)
                String encodedMsg = startNodeMsg.encode();
                
                // Send via TCP using the TCP address from HelloMsg (nodes are listening for StartNodeMsg on TCP)
                nodeTcpCommunication.sendMessage(nodeTcpAddress, encodedMsg);
                
                System.out.println("[Supervisor] Sent StartNodeMsg to node " + nodeId + " at " + nodeTcpAddressStr + " via TCP");
            }
            
            // Store network structure for later use (rounds, etc.)
            // In local mode, system already exists with NetworkStructureManager
            // In distributed mode, we don't create NetworkEmulator (nodes are remote)
            // We just use the NetworkStructureManager directly for topology info
            if (system == null || system.getNetworkStructureManager() == null) {
                // Distributed mode: don't create NetworkEmulator, just use nsm directly
                // system will remain null in distributed mode
                System.out.println("[Supervisor] Distributed mode: No NetworkEmulator (nodes are remote)");
            } else {
                // Local mode: system already exists with NetworkStructureManager
                System.out.println("[Supervisor] Local mode: Using existing NetworkEmulator");
            }
            
            // Send structural information to UI (using local nsm)
            sendStructuralInfosToUiDistributed(nsm, N);
            
            // Wait for nodes to initialize
            try {
                System.out.println("Waiting for nodes to initialize...");
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // Initialize round 0 and register source nodes
            currentRound = 0;
            if (gui != null) {
                registerSourceNodesInRound0Distributed(nsm);
            }
        
        // Start sending start_round messages
        isNetworkRunning = true;
        startRoundThread = Thread.startVirtualThread(this::sendStartRoundPeriodically);
            
        } catch (Exception e) {
            System.err.println("[Supervisor] Error finishing SEARCHING mode: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Generate subscribed topics for all nodes (all source topics)
     */
    private List<MessageTopic> generateSubscribedTopics(NetworkStructureManager nsm) {
        List<MessageTopic> topics = new ArrayList<>();
        Set<Integer> sourceNodesId = nsm.getSourceNodesId();
        for (Integer sourceId : sourceNodesId) {
            String subject = nsm.getSubjectForNode(sourceId);
            if (subject != null) {
                topics.add(new MessageTopic(subject, sourceId));
            }
        }
        return topics;
    }
    
    /**
     * Send structural information to UI (distributed mode)
     */
    private void sendStructuralInfosToUiDistributed(NetworkStructureManager nsm, int N) {
        try {
            // Create structural info matrix from NetworkStructureManager
            Map<Integer, List<Integer>> adjMap = new HashMap<>();
            Map<Integer, String> nodeIdToSubject = new HashMap<>();
            
            for (int i = 0; i < N; i++) {
                adjMap.put(i, nsm.getNeighbors(i));
                String subject = nsm.getSubjectForNode(i);
                if (subject != null) {
                    nodeIdToSubject.put(i, subject);
                }
            }
            
            supervisor.network_emulation.neighbors_and_subject.StructuralInfosMatrix matrix = 
                new supervisor.network_emulation.neighbors_and_subject.StructuralInfosMatrix(adjMap, nodeIdToSubject);
            
            StructuralInfosMsg msg = StructuralInfosMsg.fromStructuralInfosMatrix(matrix);
            String encodedMsg = msg.encode();
            
            sendToUi(encodedMsg);
        } catch (Exception e) {
            System.err.println("Error sending structural infos to UI (distributed): " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Register source nodes as infected in round 0 (distributed mode)
     */
    private void registerSourceNodesInRound0Distributed(NetworkStructureManager nsm) {
        Set<Integer> sourceNodesId = nsm.getSourceNodesId();
        for (Integer nodeId : sourceNodesId) {
            String subject = nsm.getSubjectForNode(nodeId);
            if (subject != null) {
                gui.recordInfection(nodeId, nodeId, subject, nodeId, 0, "SOURCE", 0);
                System.out.println("Registered SOURCE node " + nodeId + " with subject '" + subject + "' in round 0");
            }
        }
    }
    
    // Send structural information to UI
    private void sendStructuralInfosToUi() {
        try {
            supervisor.network_emulation.neighbors_and_subject.StructuralInfosMatrix matrix = system.getStructuralInfosMatrix();
            StructuralInfosMsg msg = StructuralInfosMsg.fromStructuralInfosMatrix(matrix);
            String encodedMsg = msg.encode();
            
            // TCP has no size limit like UDP, so we can send large messages
            sendToUi(encodedMsg);
        } catch (Exception e) {
            System.err.println("Error sending structural infos to UI: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendStartRoundPeriodically() {
        try {
            // Loop continues while network is running (both local and distributed modes)
            while (isNetworkRunning && (system != null || isDistributedMode)) {
                currentRound++;
                System.out.println("\n=== Starting Round " + currentRound + " ===");
                
                if (gui != null) {
                    gui.incrementRound();
                }
                
                StartRoundMsg startRoundMsg = new StartRoundMsg(
                    epidemic_core.message.common.Direction.supervisor_to_node.toString(),
                    epidemic_core.message.supervisor_to_node.SupervisorToNodeMessageType.start_round.toString()
                );
                String encodedMsg = startRoundMsg.encode();
                
                Map<Integer, Address> nodeAddresses;
                // Use discovered addresses (filled in finishSearchingAndSendStartNodeMsg for both modes)
                if (discoveredNodeAddresses != null && !discoveredNodeAddresses.isEmpty()) {
                    nodeAddresses = discoveredNodeAddresses;
                } else {
                    // Fallback: Use NetworkEmulator addresses in local mode (if discoveredAddresses not yet filled)
                    nodeAddresses = system != null ? system.getNodeAddresses() : new HashMap<>();
                }
                
                int nodesCount = nodeAddresses.size();
                for (Map.Entry<Integer, Address> entry : nodeAddresses.entrySet()) {
                    Address nodeAddress = entry.getValue();
                    if (nodeAddress != null) {
                        nodeCommunication.sendMessage(nodeAddress, encodedMsg);
                    }
                }
                
                System.out.println("Round " + currentRound + " triggered for all " + nodesCount + " nodes");
                
                // Sleep for round interval, but check if interrupted
                try {
                Thread.sleep(roundInterval * 1000); // Sleep for 2 seconds
                } catch (InterruptedException e) {
                    // Thread was interrupted (likely by endNetwork), exit the loop
                    Thread.currentThread().interrupt();
                    System.out.println("Round sending thread interrupted. Stopping rounds.");
                    break;
                }
                
                // Check again if network is still running after sleep
                if (!isNetworkRunning) {
                    System.out.println("Network stopped. Exiting round loop.");
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("Error in start round message: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Get the GUI instance (for Worker to record infections)
     */
    public SupervisorGui getGui() {
        return gui;
    }
    
    /**
     * Get current round number
     */
    public int getCurrentRound() {
        return currentRound;
    }
    
    
    // Send message to UI (using TCP)
    public void sendToUi(String encodedMessage) {
        // If external UI is not available, skip sending (local GUI still works)
        if (!externalUiAvailable) {
            return;
        }
        
        Address uiAddress = getUiAddress();
        if (uiAddress == null) {
            // UI address not set, disable external UI sending
            externalUiAvailable = false;
            return;
        }
        
        // TCP has no size limit, so we can send large messages safely
        // Note: TcpCommunication.sendMessage() will silently handle connection failures
        // We track failures separately to disable sending after first failure
        uiCommunication.sendMessage(uiAddress, encodedMessage);
    }
    
    /**
     * Call this method when UI connection fails to disable future attempts
     */
    public void disableExternalUi() {
        if (externalUiAvailable) {
            System.out.println("External UI not available. Disabling external UI message sending. (Local GUI still active)");
            externalUiAvailable = false;
        }
    }
    
    // stop network
    public void endNetwork(EndMsg endMessage){
        isNetworkRunning = false;
        if (startRoundThread != null) {
            startRoundThread.interrupt();
        }
        if (searchingThread != null) {
            searchingThread.interrupt();
        }
        
        // Send KillNodeMsg to all discovered nodes via TCP (if in distributed mode)
        if (isDistributedMode && addressToNodeId != null && !addressToNodeId.isEmpty() && nodeTcpCommunication != null) {
            try {
                KillNodeMsg killNodeMsg = new KillNodeMsg(
                    epidemic_core.message.common.Direction.supervisor_to_node.toString(),
                    epidemic_core.message.supervisor_to_node.SupervisorToNodeMessageType.kill_node.toString()
                );
                String encodedMsg = killNodeMsg.encode();
                
                // Send KillNodeMsg to all discovered nodes via TCP (using TCP addresses from HelloMsg)
                // Calculate TCP address from HelloMsg address (stored in addressToNodeId)
                for (Map.Entry<String, Integer> entry : addressToNodeId.entrySet()) {
                    String nodeTcpAddressStr = entry.getKey(); // TCP address from HelloMsg
                    Address nodeTcpAddress = Address.parse(nodeTcpAddressStr);
                    nodeTcpCommunication.sendMessage(nodeTcpAddress, encodedMsg);
                    System.out.println("[Supervisor] Sent KillNodeMsg to node at " + nodeTcpAddressStr + " via TCP");
                }
            } catch (Exception e) {
                System.err.println("[Supervisor] Error sending KillNodeMsg: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        if (system != null) {
            system.stopNetwork();
        }
        if (nodeTcpCommunication != null) {
            nodeTcpCommunication.closeSocket();
            nodeTcpCommunication = null;
        }
        // Clear discovered nodes
        if (addressToNodeId != null) addressToNodeId.clear();
        if (discoveredNodeAddresses != null) discoveredNodeAddresses.clear();
        if (discoveredNodeTcpAddresses != null) discoveredNodeTcpAddresses.clear();
        isSearching = false;
    }

    public void startSystem(){ // run in main
        System.out.println("[Supervisor] Starting system threads...");
        listener.startListening(); // Start UDP and TCP listener threads
        dispatcherThread = Thread.startVirtualThread(dispatcher::dispatchingLoop);
        System.out.println("[Supervisor] Dispatcher thread started");
        workerThread = Thread.startVirtualThread(worker::generalFsmLogic);
        System.out.println("[Supervisor] Worker thread started");
    }
    
    /**
     * Initialize the supervisor sockets to listen for incoming messages
     * @param supervisorPort Port number for supervisor to listen on (UDP for nodes, TCP for UI on same port)
     */
    public void initialize(int supervisorPort) {
        this.supervisorPort = supervisorPort;
        Address supervisorAddress = new Address("127.0.0.1", supervisorPort);
        
        // Setup UDP socket for nodes
        nodeCommunication.setupSocket(supervisorAddress);
        System.out.println("Supervisor UDP socket initialized for nodes on " + supervisorAddress.getIp() + ":" + supervisorAddress.getPort());
        
        // Setup TCP socket for UI (on same port - TCP and UDP can share ports)
        uiCommunication.setupSocket(supervisorAddress);
        System.out.println("Supervisor TCP server initialized for UI on " + supervisorAddress.getIp() + ":" + supervisorAddress.getPort());
    }
    
    /**
     * Main method to run the supervisor
     * @param args Command line arguments: [supervisorPort] (default: 7000)
     */
    public static void main(String[] args) {
        // Default supervisor port
        int supervisorPort = 7000;
        
        // Parse command line arguments
        if (args.length > 0) {
            try {
                supervisorPort = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0] + ". Using default port 7000.");
            }
        }
        
        // Create supervisor instance
        Supervisor supervisor = new Supervisor();
        
        // Initialize supervisor socket
        supervisor.initialize(supervisorPort);
        
        // Start the system (listener, dispatcher, worker threads)
        supervisor.startSystem();
        
        System.out.println("Supervisor is running. Waiting for messages from UI and nodes...");
        System.out.println("Press Ctrl+C to stop.");
        
        // Keep the main thread alive
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("Supervisor interrupted. Shutting down...");
            Thread.currentThread().interrupt();
        }
    }
}
