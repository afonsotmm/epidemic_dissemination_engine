package supervisor;

import epidemic_core.message.common.MessageDispatcher;
import epidemic_core.message.common.MessageTopic;
import epidemic_core.message.common.NodeToNodeMessageCounter;
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
import supervisor.server.WebSocketServerImpl;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Orchestrator responsible for managing communication infrastructure
 * and controlling network topology initialization and lifecycle
 */

public class Supervisor {

    private Communication nodeCommunication;
    private Communication uiCommunication;

    private Listener listener;
    private Dispatcher dispatcher;
    private Worker worker;
    private NetworkEmulator system;

    private BlockingQueue<String> udpMsgsQueue; // Messages from UDP (nodes)
    private BlockingQueue<String> tcpMsgsQueue; // Messages from TCP (UI)
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
    private WebSocketServerImpl webSocketServer;
    private SupervisorGui gui;
    private int currentRound = 0;
    private volatile boolean externalUiAvailable = true;

    // Distributed deployment mode fields
    private TcpCommunication nodeTcpCommunication; // TCP server for nodes (distributed mode)
    private Map<String, Integer> addressToNodeId;  // for discovered nodes
    private Map<Integer, Address> discoveredNodeAddresses; // for StartRoundMsg
    private Map<Integer, Address> discoveredNodeTcpAddresses; // for StartNodeMsg
    private volatile boolean isSearching = false;
    private Thread searchingThread;
    private static final int SEARCHING_TIMEOUT_MS = 20000;
    private volatile boolean isDistributedMode = false; // Flag to track deployment mode

    public Supervisor() {
        // buffers:
        this.udpMsgsQueue = new LinkedBlockingQueue<>();
        this.tcpMsgsQueue = new LinkedBlockingQueue<>();
        this.nodeQueue = new LinkedBlockingQueue<>();
        this.uiQueue = new LinkedBlockingQueue<>();

        this.nodeCommunication = new UdpCommunication();
        this.uiCommunication = new TcpCommunication();

        this.listener = new Listener(this, udpMsgsQueue, tcpMsgsQueue);
        this.dispatcher = new Dispatcher(udpMsgsQueue, tcpMsgsQueue, nodeQueue, uiQueue);
        this.worker = new Worker(this, nodeQueue, uiQueue);
    }

    public Communication getNodeCommunication() {return nodeCommunication;}

    public Communication getUiCommunication() { return uiCommunication; }

    public Communication getNodeTcpCommunication() {return nodeTcpCommunication;}

    public Address getUiAddress() {return startMessage != null ? startMessage.getAddr() : null;}

    // initialize topology and nodes
    public void startNetwork(StartMsg startMessage) {
        this.startMessage = startMessage;
        NodeToNodeMessageCounter.getInstance().reset();

        gui = new SupervisorGui(startMessage.getN());

        // Check deployment mode
        String deployment = startMessage.getDeployment();
        if (deployment == null || "local".equals(deployment)) {
            // local mode
            isDistributedMode = false;
            startLocalNetwork(startMessage);
        } else if ("distributed".equals(deployment)) {
            // distributed mode
            isDistributedMode = true;
            startDistributedNetwork(startMessage);
        } else {
            System.err.println("Unknown deployment mode: " + deployment + ". Using local mode.");
            isDistributedMode = false;
            startLocalNetwork(startMessage);
        }
    }

    // Local mode: Create DistributedNodeStub locally, then enter SEARCHING mode
    private void startLocalNetwork(StartMsg startMessage) {
        Address supervisorAddress = new Address("127.0.0.1", supervisorPort);
        system = new NetworkEmulator(startMessage.getN(), startMessage.getSourceNodes(), startMessage.getTopology(),
                startMessage.getProtocol(), startMessage.getMode(), supervisorAddress);

        system.initializeNetwork();

        nodeTcpCommunication = new TcpCommunication();
        Address supervisorTcpAddress = new Address("127.0.0.1", supervisorTcpPort);
        nodeTcpCommunication.setupSocket(supervisorTcpAddress);
        System.out.println("[Supervisor] TCP server initialized for nodes on port " + supervisorTcpPort);

        listener.startNodeTcpListening();

        addressToNodeId = new HashMap<>();
        discoveredNodeAddresses = new HashMap<>();
        discoveredNodeTcpAddresses = new HashMap<>();

        isSearching = true;
        startSearchingMode(startMessage);
    }

    // Distributed mode: Discover nodes via HelloMsg and send StartNodeMsg
    private void startDistributedNetwork(StartMsg startMessage) {
        System.out.println("[Supervisor] Starting distributed network discovery...");

        nodeTcpCommunication = new TcpCommunication();
        Address supervisorTcpAddress = new Address("127.0.0.1", supervisorTcpPort);
        nodeTcpCommunication.setupSocket(supervisorTcpAddress);
        System.out.println("[Supervisor] TCP server initialized for nodes on port " + supervisorTcpPort);

        listener.startNodeTcpListening();

        addressToNodeId = new HashMap<>();
        discoveredNodeAddresses = new HashMap<>();
        discoveredNodeTcpAddresses = new HashMap<>();

        isSearching = true;
        startSearchingMode(startMessage);
    }

    // Register source nodes as infected in round 0 (like old supervisor)
    private void registerSourceNodesInRound0() {
        if (system == null)
            return;

        supervisor.network_emulation.neighbors_and_subject.NetworkStructureManager nsm = system
                .getNetworkStructureManager();
        if (nsm == null)
            return;

        Set<Integer> sourceNodesId = nsm.getSourceNodesId();
        for (Integer nodeId : sourceNodesId) {
            String subject = nsm.getSubjectForNode(nodeId);
            if (subject != null) {
                gui.recordInfection(nodeId, nodeId, subject, nodeId, 0, "SOURCE", 0);
                System.out.println("Registered SOURCE node " + nodeId + " with subject '" + subject + "' in round 0");
            }
        }
    }

    // Start SEARCHING mode: Listen for HelloMsg and map addresses to Node IDs
    private void startSearchingMode(StartMsg startMessage) {
        System.out.println("[Supervisor] Entering SEARCHING mode - listening for HelloMsg from nodes...");

        int targetN = startMessage.getN();
        long startTime = System.currentTimeMillis();

        searchingThread = Thread.startVirtualThread(() -> {
            while (isSearching && addressToNodeId.size() < targetN) {

                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed >= SEARCHING_TIMEOUT_MS) {
                    System.out.println("[Supervisor] SEARCHING timeout reached (2 minutes). Found "
                            + addressToNodeId.size() + " nodes.");
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

    // Handle HelloMsg: Map TCP address to Node ID and store both TCP and UDP addresses
    public void handleHelloMsg(HelloMsg helloMsg) {

        String tcpAddress = helloMsg.getTcpAddress();
        String udpAddress = helloMsg.getUdpAddress();

        if (tcpAddress == null || addressToNodeId.containsKey(tcpAddress)) {
            return; // Already mapped or invalid address
        }

        int nodeId = addressToNodeId.size();
        addressToNodeId.put(tcpAddress, nodeId);

        Address tcpAddr = Address.parse(tcpAddress);
        Address udpAddr = udpAddress != null ? Address.parse(udpAddress) : null;

        if (udpAddr == null) {
            System.err.println("[Supervisor] Warning: HelloMsg missing UDP address for TCP address: " + tcpAddress);
            return;
        }

        discoveredNodeTcpAddresses.put(nodeId, tcpAddr);
        discoveredNodeAddresses.put(nodeId, udpAddr);

        if (gui != null) {
            gui.recordDiscoveredNode(nodeId, tcpAddress, udpAddress);
        }

        System.out.println("[Supervisor] Discovered node: TCP=" + tcpAddress + ", UDP=" + udpAddress + " -> Node ID: "
                + nodeId + " (Total: " + addressToNodeId.size() + "/"
                + (startMessage != null ? startMessage.getN() : "?") + ")");
    }

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
            String protocolRaw = startMessage.getProtocol();
            String mode = startMessage.getMode();

            String protocol = "gossip".equalsIgnoreCase(protocolRaw) ? "blind_coin" : protocolRaw;
            if (protocol != null) {
                protocol = protocol.replace('-', '_');
            }

            supervisor.network_emulation.neighbors_and_subject.NetworkStructureManager nsm;

            if (system != null && system.getNetworkStructureManager() != null) {
                nsm = system.getNetworkStructureManager();
                System.out.println("[Supervisor] Using existing NetworkStructureManager (local mode)");
            } else {
                TopologyType type = TopologyType.fromString(topologyType);
                Topology topology = new Topology();
                Map<Integer, List<Integer>> adjMap = topology.createTopology(type, N);

                nsm = new supervisor.network_emulation.neighbors_and_subject.NetworkStructureManager(adjMap,
                        sourceNodes, N);
                System.out.println("[Supervisor] Created new NetworkStructureManager (distributed mode)");
            }

            NodeIdToAddressTable nodeIdToAddressTable = new NodeIdToAddressTable(N);
            Map<Integer, Address> addressTable = nodeIdToAddressTable.getAll();

            for (Map.Entry<Integer, Address> entry : discoveredNodeAddresses.entrySet()) {
                int nodeId = entry.getKey();
                Address udpAddress = entry.getValue();
                addressTable.put(nodeId, udpAddress);
            }

            List<MessageTopic> subscribedTopics = generateSubscribedTopics(nsm);

            Address supervisorTcpAddress = new Address("127.0.0.1", supervisorTcpPort);
            String supervisorTcpAddressStr = supervisorTcpAddress.getIp() + ":" + supervisorTcpAddress.getPort();

            Double k = null;
            if (protocol != null && (protocol.contains("coin") || protocol.contains("gossip"))) {
                k = 2.0; // Change here k value
            }

            for (Map.Entry<Integer, Address> entry : discoveredNodeTcpAddresses.entrySet()) {
                int nodeId = entry.getKey();
                Address nodeTcpAddress = entry.getValue();
                String nodeTcpAddressStr = nodeTcpAddress.getIp() + ":" + nodeTcpAddress.getPort();

                List<Integer> neighbors = nsm.getNeighbors(nodeId);
                String assignedSubjectAsSource = nsm.getSubjectForNode(nodeId);

                Map<String, String> nodeToAddressTableStr = new HashMap<>();
                for (Map.Entry<Integer, Address> addrEntry : addressTable.entrySet()) {
                    nodeToAddressTableStr.put(String.valueOf(addrEntry.getKey()),
                            addrEntry.getValue().getIp() + ":" + addrEntry.getValue().getPort());
                }

                List<Map<String, Object>> subscribedTopicsJson = new ArrayList<>();
                for (MessageTopic topic : subscribedTopics) {
                    Map<String, Object> topicMap = new HashMap<>();
                    topicMap.put("subject", topic.subject());
                    topicMap.put("sourceId", topic.sourceId());
                    subscribedTopicsJson.add(topicMap);
                }

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
                        k);

                String encodedMsg = startNodeMsg.encode();

                nodeTcpCommunication.sendMessage(nodeTcpAddress, encodedMsg);

                System.out.println("[Supervisor] Sent StartNodeMsg to node " + nodeId + " at " + nodeTcpAddressStr + " via TCP");
            }

            if (system == null || system.getNetworkStructureManager() == null) {
                System.out.println("[Supervisor] Distributed mode: No NetworkEmulator (nodes are remote)");
            } else {
                System.out.println("[Supervisor] Local mode: Using existing NetworkEmulator");
            }

            sendStructuralInfosToUiDistributed(nsm, N);

            try {
                System.out.println("Waiting for nodes to initialize...");
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            currentRound = 0;
            if (gui != null) {
                registerSourceNodesInRound0Distributed(nsm);
            }

            isNetworkRunning = true;
            startRoundThread = Thread.startVirtualThread(this::sendStartRoundPeriodically);

        } catch (Exception e) {
            System.err.println("[Supervisor] Error finishing SEARCHING mode: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Generate subscribed topics for all nodes (all source topics)
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

    // Send structural information to UI (distributed mode)
    private void sendStructuralInfosToUiDistributed(NetworkStructureManager nsm, int N) {
        try {
            Map<Integer, List<Integer>> adjMap = new HashMap<>();
            Map<Integer, String> nodeIdToSubject = new HashMap<>();

            for (int i = 0; i < N; i++) {
                adjMap.put(i, nsm.getNeighbors(i));
                String subject = nsm.getSubjectForNode(i);
                if (subject != null) {
                    nodeIdToSubject.put(i, subject);
                }
            }

            supervisor.network_emulation.neighbors_and_subject.StructuralInfosMatrix matrix = new supervisor.network_emulation.neighbors_and_subject.StructuralInfosMatrix(
                    adjMap, nodeIdToSubject);

            StructuralInfosMsg msg = StructuralInfosMsg.fromStructuralInfosMatrix(matrix);
            String encodedMsg = msg.encode();

            sendToUi(encodedMsg);
        } catch (Exception e) {
            System.err.println("Error sending structural infos to UI (distributed): " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Register source nodes as infected in round 0 (distributed mode)
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
            supervisor.network_emulation.neighbors_and_subject.StructuralInfosMatrix matrix = system
                    .getStructuralInfosMatrix();
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
                        epidemic_core.message.supervisor_to_node.SupervisorToNodeMessageType.start_round.toString());
                String encodedMsg = startRoundMsg.encode();

                Map<Integer, Address> nodeAddresses;

                if (discoveredNodeAddresses != null && !discoveredNodeAddresses.isEmpty()) {
                    nodeAddresses = discoveredNodeAddresses;
                } else {
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

                // Send StartRoundMsg to UI as well
                if (gui != null) {
                    StartRoundMsg startRoundMsgUi = new StartRoundMsg(
                            epidemic_core.message.common.Direction.supervisor_to_ui.toString(),
                            epidemic_core.message.supervisor_to_node.SupervisorToNodeMessageType.start_round
                                    .toString());
                    sendToUi(startRoundMsgUi.encode());
                }

                try {
                    Thread.sleep(roundInterval * 1000); // Sleep for 2 seconds
                } catch (InterruptedException e) {
                    // Thread was interrupted (likely by endNetwork), exit the loop
                    Thread.currentThread().interrupt();
                    System.out.println("Round sending thread interrupted. Stopping rounds.");
                    break;
                }

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

    public SupervisorGui getGui() {return gui;}

    public int getCurrentRound() {return currentRound; }

    public void sendToUi(String encodedMessage) {
        if (!externalUiAvailable) {
            return;
        }

        Address uiAddress = getUiAddress();
        if (uiAddress == null) {
            externalUiAvailable = false;
            return;
        }

        if (uiAddress.getPort() != 0) {
            uiCommunication.sendMessage(uiAddress, encodedMessage);
        }

        if (webSocketServer != null) {
            webSocketServer.broadcast(encodedMessage);
        }
    }

    // stop network
    public void endNetwork(EndMsg endMessage) {
        isNetworkRunning = false;
        if (startRoundThread != null) {
            startRoundThread.interrupt();
        }
        if (searchingThread != null) {
            searchingThread.interrupt();
        }

        // Send KillNodeMsg to all discovered nodes via TCP (if in distributed mode)
        if (isDistributedMode && addressToNodeId != null && !addressToNodeId.isEmpty()
                && nodeTcpCommunication != null) {
            try {
                KillNodeMsg killNodeMsg = new KillNodeMsg(
                        epidemic_core.message.common.Direction.supervisor_to_node.toString(),
                        epidemic_core.message.supervisor_to_node.SupervisorToNodeMessageType.kill_node.toString());
                String encodedMsg = killNodeMsg.encode();

                for (Map.Entry<String, Integer> entry : addressToNodeId.entrySet()) {
                    String nodeTcpAddressStr = entry.getKey();
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
        if (addressToNodeId != null)
            addressToNodeId.clear();
        if (discoveredNodeAddresses != null)
            discoveredNodeAddresses.clear();
        if (discoveredNodeTcpAddresses != null)
            discoveredNodeTcpAddresses.clear();
        isSearching = false;
    }

    public void startSystem() { // run in main
        System.out.println("[Supervisor] Starting system threads...");
        listener.startListening(); // Start UDP and TCP listener threads
        dispatcherThread = Thread.startVirtualThread(dispatcher::dispatchingLoop);
        System.out.println("[Supervisor] Dispatcher thread started");
        workerThread = Thread.startVirtualThread(worker::generalFsmLogic);
        System.out.println("[Supervisor] Worker thread started");
    }

    public void initialize(int supervisorPort) {
        this.supervisorPort = supervisorPort;
        Address supervisorAddress = new Address("127.0.0.1", supervisorPort);

        nodeCommunication.setupSocket(supervisorAddress);
        System.out.println("Supervisor UDP socket initialized for nodes on " + supervisorAddress.getIp() + ":"
                + supervisorAddress.getPort());

        uiCommunication.setupSocket(supervisorAddress);
        System.out.println("Supervisor TCP server initialized for UI on " + supervisorAddress.getIp() + ":"
                + supervisorAddress.getPort());

        webSocketServer = new WebSocketServerImpl(8087, uiQueue);
        webSocketServer.start();
        System.out.println("Supervisor WebSocket server initialized on port 8087");
    }

    public static void main(String[] args) {
        int supervisorPort = 7000;

        if (args.length > 0) {
            try {
                supervisorPort = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0] + ". Using default port 7000.");
            }
        }

        Supervisor supervisor = new Supervisor();
        supervisor.initialize(supervisorPort);

        supervisor.startSystem();

        System.out.println("Supervisor is running. Waiting for messages from UI and nodes...");
        System.out.println("Press Ctrl+C to stop.");

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("Supervisor interrupted. Shutting down...");
            Thread.currentThread().interrupt();
        }
    }
}
