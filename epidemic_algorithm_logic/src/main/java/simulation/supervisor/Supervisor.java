package simulation.supervisor;

import epidemic_core.node.Node;
import epidemic_core.node.mode.pull.anti_entropy.AntiEntropyPullNode;
import epidemic_core.node.mode.pull.gossip.blind.coin.BlindCoinPullNode;
import epidemic_core.node.mode.pull.gossip.feedback.coin.FeedbackCoinPullNode;
import epidemic_core.node.mode.push.anti_entropy.AntiEntropyPushNode;
import epidemic_core.node.mode.push.gossip.blind.coin.BlindCoinPushNode;
import epidemic_core.node.mode.push.gossip.feedback.coin.FeedbackCoinPushNode;
import epidemic_core.node.mode.pushpull.anti_entropy.AntiEntropyPushPullNode;
import epidemic_core.node.mode.pushpull.gossip.blind.coin.BlindCoinPushPullNode;
import epidemic_core.node.mode.pushpull.gossip.feedback.coin.FeedbackCoinPushPullNode;
import epidemic_core.message.common.MessageTopic;
import general.communication.Communication;
import general.communication.implementation.UdpCommunication;
import general.communication.utils.Address;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;

/**
 * Supervisor that manages and monitors epidemic dissemination nodes.
 * 
 * Features:
 * - Creates N nodes, each running in its own thread
 * - Builds address table with localhost and different ports for each node
 * - Creates complete graph topology (all nodes connected to all)
 * - Receives INFECTED messages from nodes
 * - Stores infection information
 */
public class Supervisor {

    private final int numberOfNodes;
    private final int nodePort; // Fixed port for all nodes
    private final Address supervisorAddress;
    private final Communication supervisorCommunication;

    // Storage for infection information: Map<nodeId, List<InfectionRecord>>
    private final Map<Integer, List<InfectionRecord>> infectionHistory;

    // Storage for remotion information: Map<nodeId, Set<MessageId>> (nodes that
    // have removed messages)
    private final Map<Integer, Set<epidemic_core.message.common.MessageId>> remotionHistory;

    // Node mode enum
    public enum NodeMode {
        PULL,
        PUSH,
        PUSHPULL,
        BLIND_COIN_PUSH, // Gossip protocol: Blind Coin Push
        BLIND_COIN_PULL, // Gossip protocol: Blind Coin Pull
        BLIND_COIN_PUSHPULL, // Gossip protocol: Blind Coin PushPull
        FEEDBACK_COIN_PUSH, // Gossip protocol: Feedback Coin Push
        FEEDBACK_COIN_PULL, // Gossip protocol: Feedback Coin Pull
        FEEDBACK_COIN_PUSHPULL // Gossip protocol: Feedback Coin PushPull
    }

    // Map to store all nodes
    private final Map<Integer, Node> nodes;

    // Map to store which nodes are sources and their subjects (for GUI, without
    // direct node access)
    private final Map<Integer, String> sourceNodes; // nodeId -> subject

    // Node mode
    private final NodeMode nodeMode;

    // Blind Coin parameter k (probability 1/k to stop spreading)
    private final double blindCoinK;

    // Round management
    private int currentRound = 0;
    private final Map<Integer, Set<Integer>> infectionsPerRound; // Map<round, Set<nodeIds>>
    private ScheduledExecutorService roundScheduler;
    private double roundInterval = 1.0; // Round interval in seconds (default 1.0)

    // Track unique infected nodes (to avoid counting updates as new infections)
    private final Set<Integer> uniqueInfectedNodes; // Set of nodeIds that have been infected at least once

    // Track last round with new infections (for stopping condition: 2 rounds
    // without infections)
    private int lastRoundWithInfections = -1;

    // Address table for all nodes
    private final Map<Integer, Address> addressTable;

    // Chart frame for auto-updating
    private ChartFrame chartFrame;
    private JFreeChart currentChart;
    private volatile boolean chartAutoUpdate = false;

    // System start time (when nodes start running)
    private LocalDateTime systemStartTime;

    /**
     * Record to store infection information
     */
    public static class InfectionRecord {
        private final int nodeId;
        private final String subject;
        private final int timestamp;
        private final int sourceId;
        private final LocalDateTime infectionTime;
        private final int round;

        public InfectionRecord(int nodeId, String subject, int timestamp, int sourceId, int round) {
            this.nodeId = nodeId;
            this.subject = subject;
            this.timestamp = timestamp;
            this.sourceId = sourceId;
            this.infectionTime = LocalDateTime.now();
            this.round = round;
        }

        public int getNodeId() {
            return nodeId;
        }

        public String getSubject() {
            return subject;
        }

        public int getTimestamp() {
            return timestamp;
        }

        public int getSourceId() {
            return sourceId;
        }

        public LocalDateTime getInfectionTime() {
            return infectionTime;
        }

        public int getRound() {
            return round;
        }

        @Override
        public String toString() {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return String.format("Node %d infected with subject '%s' from source %d (timestamp: %d) at round %d - %s",
                    nodeId, subject, sourceId, timestamp, round, infectionTime.format(formatter));
        }
    }

    /**
     * Constructor
     * 
     * @param numberOfNodes  Number of nodes to create
     * @param nodePort       Port number for all nodes (same port, different IPs)
     * @param supervisorPort Port for supervisor to listen on
     * @param nodeMode       Mode of operation: PULL, PUSH, PUSHPULL, or
     *                       BLIND_COIN_PUSH
     */
    public Supervisor(int numberOfNodes, int nodePort, int supervisorPort, NodeMode nodeMode) {
        this(numberOfNodes, nodePort, supervisorPort, nodeMode, 3.0); // Default k=3.0 for Blind Coin
    }

    /**
     * Constructor with Blind Coin parameter
     * 
     * @param numberOfNodes  Number of nodes to create
     * @param nodePort       Port number for all nodes (same port, different IPs)
     * @param supervisorPort Port for supervisor to listen on
     * @param nodeMode       Mode of operation: PULL, PUSH, PUSHPULL, or
     *                       BLIND_COIN_PUSH
     * @param blindCoinK     Probability parameter for Blind Coin (1/k chance to
     *                       stop spreading)
     */
    public Supervisor(int numberOfNodes, int nodePort, int supervisorPort, NodeMode nodeMode, double blindCoinK) {
        this.numberOfNodes = numberOfNodes;
        this.nodePort = nodePort;
        this.nodeMode = nodeMode;
        this.blindCoinK = blindCoinK;
        this.supervisorAddress = new Address("127.0.0.1", supervisorPort);
        this.supervisorCommunication = new UdpCommunication();
        this.infectionHistory = new ConcurrentHashMap<>();
        this.remotionHistory = new ConcurrentHashMap<>();
        this.nodes = new ConcurrentHashMap<>();
        this.sourceNodes = new ConcurrentHashMap<>();
        this.addressTable = new HashMap<>();
        this.infectionsPerRound = new ConcurrentHashMap<>();
        this.uniqueInfectedNodes = ConcurrentHashMap.newKeySet();

        // Initialize infection history and remotion history for all nodes
        for (int i = 1; i <= numberOfNodes; i++) {
            infectionHistory.put(i, Collections.synchronizedList(new ArrayList<>()));
            remotionHistory.put(i, ConcurrentHashMap.newKeySet());
        }
    }

    /**
     * Initialize the supervisor: start listening for messages
     */
    public void initialize() {
        supervisorCommunication.setupSocket(supervisorAddress);
        System.out.println("Supervisor initialized and listening on " + supervisorAddress.getIp() + ":"
                + supervisorAddress.getPort());
    }

    /**
     * Convert nodeId to a localhost IP address
     * For nodeId <= 255: uses 127.0.0.nodeId
     * For nodeId > 255: uses 127.0.X.0 where X = (nodeId - 1) / 255
     * 
     * Examples:
     * - nodeId 1 -> 127.0.0.1
     * - nodeId 255 -> 127.0.0.255
     * - nodeId 256 -> 127.0.1.0
     * - nodeId 257 -> 127.0.1.1
     * - nodeId 510 -> 127.0.1.255
     * - nodeId 511 -> 127.0.2.0
     */
    private String nodeIdToLocalhostIp(int nodeId) {
        if (nodeId <= 255) {
            return "127.0.0." + nodeId;
        } else {
            // For nodeId > 255: 127.0.X.Y
            // X = (nodeId - 1) / 255
            // Y = (nodeId - 1) % 255
            int x = (nodeId - 1) / 255;
            int y = (nodeId - 1) % 255;
            return "127.0." + x + "." + y;
        }
    }

    /**
     * Build address table for all nodes
     * Uses different localhost IPs (127.0.0.1, 127.0.0.2, ..., 127.0.X.0) with same
     * port
     */
    private void buildAddressTable() {
        for (int i = 1; i <= numberOfNodes; i++) {
            String ip = nodeIdToLocalhostIp(i);
            addressTable.put(i, new Address(ip, nodePort));
        }
        System.out.println("Address table built for " + numberOfNodes + " nodes (port: " + nodePort + ")");
    }

    /**
     * Build neighbours list for complete graph (all nodes connected to all)
     * 
     * @param nodeId The node ID
     * @return List of neighbour IDs
     */
    private List<Integer> buildNeighbours(int nodeId) {
        List<Integer> neighbours = new ArrayList<>();
        for (int i = 1; i <= numberOfNodes; i++) {
            if (i != nodeId) {
                neighbours.add(i);
            }
        }
        return neighbours;
    }

    /**
     * Generate subscribed topics based on all sources
     * Each node is interested in all subjects published by sources (subject +
     * sourceId)
     * 
     * @param subjectsForNodes Map of nodeId -> subject for source nodes
     * @return List of MessageTopic representing interests
     */
    private List<MessageTopic> generateSubscribedTopics(Map<Integer, String> subjectsForNodes) {
        List<MessageTopic> topics = new ArrayList<>();
        if (subjectsForNodes != null) {
            for (Map.Entry<Integer, String> entry : subjectsForNodes.entrySet()) {
                Integer sourceId = entry.getKey();
                String subject = entry.getValue();
                if (subject != null) {
                    // Create MessageTopic with subject and sourceId (timestamp not needed for
                    // subscription)
                    topics.add(new MessageTopic(subject, sourceId));
                }
            }
        }
        return topics;
    }

    /**
     * Create and start all nodes
     * 
     * @param subjectsForNodes Map of nodeId -> subject (null if node is not a
     *                         source)
     */
    public void createAndStartNodes(Map<Integer, String> subjectsForNodes) {
        buildAddressTable();

        // Generate subscribed topics based on all sources
        // Each node is interested in all subjects published by sources
        List<MessageTopic> subscribedTopics = generateSubscribedTopics(subjectsForNodes);

        System.out.println("Creating " + numberOfNodes + " nodes...");

        // Use CountDownLatch to wait for all nodes to be created
        CountDownLatch creationLatch = new CountDownLatch(numberOfNodes);

        for (int i = 1; i <= numberOfNodes; i++) {
            final int nodeId = i;

            // Get subject for this node (null if not a source)
            String subject = subjectsForNodes != null ? subjectsForNodes.get(nodeId) : null;

            // Build neighbours list (complete graph)
            List<Integer> neighbours = buildNeighbours(nodeId);

            // Create node in a separate virtual thread
            Thread.startVirtualThread(() -> {
                try {
                    // Small delay to avoid socket creation conflicts
                    Thread.sleep((nodeId - 1) * 10); // 10ms delay between each node

                    Node node;
                    if (nodeMode == NodeMode.PULL) {
                        node = new AntiEntropyPullNode(
                                nodeId,
                                neighbours,
                                subject,
                                addressTable,
                                subscribedTopics,
                                supervisorAddress);
                    } else if (nodeMode == NodeMode.PUSH) {
                        node = new AntiEntropyPushNode(
                                nodeId,
                                neighbours,
                                subject,
                                addressTable,
                                subscribedTopics,
                                supervisorAddress);
                    } else if (nodeMode == NodeMode.BLIND_COIN_PUSH) {
                        node = new BlindCoinPushNode(
                                nodeId,
                                neighbours,
                                subject,
                                addressTable,
                                subscribedTopics,
                                supervisorAddress,
                                blindCoinK);
                    } else if (nodeMode == NodeMode.BLIND_COIN_PULL) {
                        node = new BlindCoinPullNode(
                                nodeId,
                                neighbours,
                                subject,
                                addressTable,
                                subscribedTopics,
                                supervisorAddress,
                                blindCoinK);
                    } else if (nodeMode == NodeMode.BLIND_COIN_PUSHPULL) {
                        node = new BlindCoinPushPullNode(
                                nodeId,
                                neighbours,
                                subject,
                                addressTable,
                                subscribedTopics,
                                supervisorAddress,
                                blindCoinK);
                    } else if (nodeMode == NodeMode.FEEDBACK_COIN_PUSH) {
                        node = new FeedbackCoinPushNode(
                                nodeId,
                                neighbours,
                                subject,
                                addressTable,
                                subscribedTopics,
                                supervisorAddress,
                                blindCoinK);
                    } else if (nodeMode == NodeMode.FEEDBACK_COIN_PULL) {
                        node = new FeedbackCoinPullNode(
                                nodeId,
                                neighbours,
                                subject,
                                addressTable,
                                subscribedTopics,
                                supervisorAddress,
                                blindCoinK);
                    } else if (nodeMode == NodeMode.FEEDBACK_COIN_PUSHPULL) {
                        node = new FeedbackCoinPushPullNode(
                                nodeId,
                                neighbours,
                                subject,
                                addressTable,
                                subscribedTopics,
                                supervisorAddress,
                                blindCoinK);
                    } else {
                        node = new AntiEntropyPushPullNode(
                                nodeId,
                                neighbours,
                                subject,
                                addressTable,
                                subscribedTopics,
                                supervisorAddress);
                    }

                    nodes.put(nodeId, node);
                    System.out.println("Node " + nodeId + " created (" + nodeMode + ")" +
                            (subject != null ? " (SOURCE for subject: " + subject + ")" : ""));

                    // Start the node based on mode
                    if (nodeMode == NodeMode.PULL) {
                        ((AntiEntropyPullNode) node).startRunning();
                    } else if (nodeMode == NodeMode.PUSH) {
                        ((AntiEntropyPushNode) node).startRunning();
                    } else if (nodeMode == NodeMode.BLIND_COIN_PUSH) {
                        ((BlindCoinPushNode) node).startRunning();
                    } else if (nodeMode == NodeMode.BLIND_COIN_PULL) {
                        ((BlindCoinPullNode) node).startRunning();
                    } else if (nodeMode == NodeMode.BLIND_COIN_PUSHPULL) {
                        ((BlindCoinPushPullNode) node).startRunning();
                    } else if (nodeMode == NodeMode.FEEDBACK_COIN_PUSH) {
                        ((FeedbackCoinPushNode) node).startRunning();
                    } else if (nodeMode == NodeMode.FEEDBACK_COIN_PULL) {
                        ((FeedbackCoinPullNode) node).startRunning();
                    } else if (nodeMode == NodeMode.FEEDBACK_COIN_PUSHPULL) {
                        ((FeedbackCoinPushPullNode) node).startRunning();
                    } else {
                        ((AntiEntropyPushPullNode) node).startRunning();
                    }
                    System.out.println("Node " + nodeId + " started");
                } catch (Exception e) {
                    System.err.println("Error creating node " + nodeId + ": " + e.getMessage());
                    if (e.getMessage() == null || !e.getMessage().contains("Address already in use")) {
                        e.printStackTrace();
                    }
                } finally {
                    // Signal that this node creation attempt is complete
                    creationLatch.countDown();
                }
            });
        }

        // Wait for all nodes to be created and started
        try {
            System.out.println("Waiting for all " + numberOfNodes + " nodes to be created...");
            creationLatch.await(); // Wait until all nodes are created
            System.out.println("All nodes created! Waiting additional time for sockets to initialize...");
            Thread.sleep(2000); // Additional time for sockets to fully initialize
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted while waiting for nodes to be created");
        }

        // Record system start time (after all nodes are initialized)
        systemStartTime = LocalDateTime.now();

        // Initialize round 0 and register source nodes
        currentRound = 0;
        infectionsPerRound.put(0, ConcurrentHashMap.newKeySet());
        lastRoundWithInfections = 0; // Initialize with round 0 (source nodes are considered initial infections)

        // Register source nodes as infected in round 0 and store source information
        if (subjectsForNodes != null) {
            for (Map.Entry<Integer, String> entry : subjectsForNodes.entrySet()) {
                int nodeId = entry.getKey();
                String subject = entry.getValue();
                if (subject != null) {
                    // Store source information (for GUI access without direct node access)
                    sourceNodes.put(nodeId, subject);
                    // Create infection record for source node in round 0
                    InfectionRecord record = new InfectionRecord(nodeId, subject, 0, nodeId, 0);
                    infectionHistory.get(nodeId).add(record);
                    uniqueInfectedNodes.add(nodeId); // Mark as infected
                    infectionsPerRound.get(0).add(nodeId);
                    System.out
                            .println("Registered SOURCE node " + nodeId + " with subject '" + subject + "' in round 0");
                }
            }
        }

        System.out.println("All nodes created and started! Ready to begin rounds.");
    }

    /**
     * Start listening for infection_update messages from nodes
     * This should run in a separate thread
     */
    public void startMonitoring() {
        System.out.println("Supervisor started monitoring for infection_update messages...");

        new Thread(() -> {
            while (true) {
                try {
                    String receivedMessage = supervisorCommunication.receiveMessage();
                    if (receivedMessage != null) {
                        // Process infection_update messages from nodes
                        if (receivedMessage.startsWith("node_to_supervisor;infection_update")) {
                            processInfectionUpdateMessage(receivedMessage);
                        } else if (receivedMessage.startsWith("node_to_supervisor;remotion_update")) {
                            processRemotionUpdateMessage(receivedMessage);
                        }
                        // Silently ignore other message types (REQUEST, REPLY, etc.)
                    }
                } catch (Exception e) {
                    System.err.println("Error receiving message in supervisor: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * Process an infection_update message from a node
     * Format:
     * node_to_supervisor;infection_update;subject;timestamp;sourceId;updated_node_id;infecting_node_id
     * 
     * @param encodedMessage The encoded message string
     */
    private void processInfectionUpdateMessage(String encodedMessage) {
        try {
            // Decode InfectionUpdateMsg (using decodeMessage for JSON format)
            epidemic_core.message.node_to_supervisor.infection_update.InfectionUpdateMsg infectionUpdateMsg = epidemic_core.message.node_to_supervisor.infection_update.InfectionUpdateMsg
                    .decodeMessage(encodedMessage);

            int nodeId = infectionUpdateMsg.getUpdatedNodeId();
            int infectingNodeId = infectionUpdateMsg.getInfectingNodeId();
            epidemic_core.message.common.MessageId msgId = infectionUpdateMsg.getId();
            String subject = msgId.topic().subject();
            int timestamp = (int) msgId.timestamp();
            int sourceId = msgId.topic().sourceId();

            // Validate nodeId
            if (nodeId < 1 || nodeId > numberOfNodes) {
                System.err.println("Invalid nodeId in infection_update message: " + nodeId);
                return;
            }

            // Create infection record with current round
            InfectionRecord record = new InfectionRecord(nodeId, subject, timestamp, sourceId, currentRound);
            infectionHistory.get(nodeId).add(record);

            // Track infection per round ONLY if this is the first time this node gets
            // infected
            // (ignore updates to already infected nodes to show true growth curve)
            boolean isNewInfection = uniqueInfectedNodes.add(nodeId);
            if (isNewInfection) {
                infectionsPerRound.computeIfAbsent(currentRound, k -> ConcurrentHashMap.newKeySet()).add(nodeId);
                lastRoundWithInfections = currentRound; // Update last round with infections
                System.out.println("INFECTED (NEW): " + record + " (infected by node " + infectingNodeId + ")");
            } else {
                // This is an update to an already infected node - don't count as new infection
                System.out.println("INFECTED (UPDATE): " + record + " (infected by node " + infectingNodeId + ")");
            }

        } catch (Exception e) {
            System.err.println("Error processing infection_update message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Process a remotion_update message from a node
     * Format:
     * node_to_supervisor;remotion_update;subject;timestamp;sourceId;updated_node_id
     * 
     * @param encodedMessage The encoded message string
     */
    private void processRemotionUpdateMessage(String encodedMessage) {
        try {
            // Decode RemotionUpdateMsg (using decodeMessage for JSON format)
            epidemic_core.message.node_to_supervisor.remotion_update.RemotionUpdateMsg remotionUpdateMsg = epidemic_core.message.node_to_supervisor.remotion_update.RemotionUpdateMsg
                    .decodeMessage(encodedMessage);

            int nodeId = remotionUpdateMsg.getUpdatedNodeId();
            epidemic_core.message.common.MessageId msgId = remotionUpdateMsg.getId();

            // Validate nodeId
            if (nodeId < 1 || nodeId > numberOfNodes) {
                System.err.println("Invalid nodeId in remotion_update message: " + nodeId);
                return;
            }

            // Add to remotion history
            remotionHistory.get(nodeId).add(msgId);

            System.out.println("REMOVED: Node " + nodeId + " removed message '" +
                    msgId.topic().subject() + "' from source " + msgId.topic().sourceId() +
                    " (timestamp=" + msgId.timestamp() + ")");

        } catch (Exception e) {
            System.err.println("Error processing remotion_update message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Check if a node has any removed messages
     * 
     * @param nodeId Node ID
     * @return true if node has removed messages, false otherwise
     */
    public boolean hasRemovedMessages(int nodeId) {
        Set<epidemic_core.message.common.MessageId> removals = remotionHistory.get(nodeId);
        return removals != null && !removals.isEmpty();
    }

    /**
     * Get number of removed messages for a node
     * 
     * @param nodeId Node ID
     * @return Number of removed messages
     */
    public int getRemovedMessagesCount(int nodeId) {
        Set<epidemic_core.message.common.MessageId> removals = remotionHistory.get(nodeId);
        return removals != null ? removals.size() : 0;
    }

    /**
     * Check convergence criteria:
     * - All nodes are infected (have stored messages that can be spread), OR
     * - No nodes are infected (no messages that can be spread)
     * 
     * For Gossip nodes: only count messages that are NOT removed (can still be
     * spread)
     * 
     * @return true if convergence criteria are met, false otherwise
     */
    private boolean hasConverged() {
        int nodesWithSpreadableMessages = 0;
        int nodesWithoutSpreadableMessages = 0;

        // Check current state of all nodes (if they have messages that can be spread)
        for (Node node : nodes.values()) {
            List<epidemic_core.message.node_to_node.spread.SpreadMsg> storedMessages = node.getAllStoredMessages();

            if (storedMessages == null || storedMessages.isEmpty()) {
                nodesWithoutSpreadableMessages++;
            } else {
                // For Gossip nodes, check if there are any messages that are NOT removed
                boolean hasSpreadableMessage = false;
                if (node instanceof epidemic_core.node.GossipNode) {
                    epidemic_core.node.GossipNode gossipNode = (epidemic_core.node.GossipNode) node;
                    for (epidemic_core.message.node_to_node.spread.SpreadMsg msg : storedMessages) {
                        if (!gossipNode.isMessageRemoved(msg.getId())) {
                            hasSpreadableMessage = true;
                            break;
                        }
                    }
                } else {
                    // For non-Gossip nodes, all stored messages can be spread
                    hasSpreadableMessage = true;
                }

                if (hasSpreadableMessage) {
                    nodesWithSpreadableMessages++;
                } else {
                    nodesWithoutSpreadableMessages++;
                }
            }
        }

        // Converged if: all nodes have spreadable messages OR no nodes have spreadable
        // messages
        boolean converged = nodesWithSpreadableMessages == numberOfNodes
                || nodesWithoutSpreadableMessages == numberOfNodes;

        // Always print debug info to help diagnose
        System.out.println("[DEBUG Round " + currentRound + "] Convergence check: " +
                "nodesWithSpreadableMessages=" + nodesWithSpreadableMessages +
                ", nodesWithoutSpreadableMessages=" + nodesWithoutSpreadableMessages +
                ", numberOfNodes=" + numberOfNodes +
                ", converged=" + converged);

        return converged;
    }

    /**
     * Get infection history for a specific node
     * 
     * @param nodeId Node ID
     * @return List of infection records
     */
    public List<InfectionRecord> getInfectionHistory(int nodeId) {
        return new ArrayList<>(infectionHistory.getOrDefault(nodeId, new ArrayList<>()));
    }

    /**
     * Get all infection history
     * 
     * @return Map of nodeId to infection records
     */
    public Map<Integer, List<InfectionRecord>> getAllInfectionHistory() {
        Map<Integer, List<InfectionRecord>> copy = new HashMap<>();
        for (Map.Entry<Integer, List<InfectionRecord>> entry : infectionHistory.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }

    /**
     * Print infection statistics
     */
    public void printInfectionStatistics() {
        System.out.println("\n=== Infection Statistics ===");
        int totalInfections = 0;
        List<Integer> uninfectedNodes = new ArrayList<>();

        for (Map.Entry<Integer, List<InfectionRecord>> entry : infectionHistory.entrySet()) {
            int nodeId = entry.getKey();
            int count = entry.getValue().size();
            totalInfections += count;
            if (count > 0) {
                System.out.println("Node " + nodeId + ": " + count + " infection(s)");
            } else {
                uninfectedNodes.add(nodeId);
            }
        }

        System.out.println("Total infections: " + totalInfections);
        System.out.println("Unique infected nodes: " + uniqueInfectedNodes.size() + "/" + numberOfNodes);

        if (!uninfectedNodes.isEmpty()) {
            System.out.println("Uninfected nodes (" + uninfectedNodes.size() + "): " + uninfectedNodes);
        }

        System.out.println("===========================\n");
    }

    /**
     * Print current state of all nodes (subjects and values)
     */
    public void printNodesState() {
        System.out.println("\n=== Nodes Current State ===");
        for (Map.Entry<Integer, Node> entry : nodes.entrySet()) {
            int nodeId = entry.getKey();
            Node node = entry.getValue();
            node.printNodeState();
        }
        System.out.println("==========================\n");
    }

    /**
     * Trigger a round for all nodes (synchronized round start)
     * Sends StartRoundMsg via UDP to all nodes
     */
    public void triggerPullRound() {
        currentRound++;
        System.out.println("\n=== Starting Round " + currentRound + " (" + nodeMode + " mode) ===");

        // Initialize round tracking
        infectionsPerRound.put(currentRound, ConcurrentHashMap.newKeySet());

        // Create and send StartRoundMsg to all nodes
        epidemic_core.message.supervisor_to_node.start_round.StartRoundMsg startRoundMsg = new epidemic_core.message.supervisor_to_node.start_round.StartRoundMsg(
                epidemic_core.message.common.Direction.supervisor_to_node.toString(),
                epidemic_core.message.supervisor_to_node.SupervisorToNodeMessageType.start_round.toString());
        String encodedMsg;
        try {
            encodedMsg = startRoundMsg.encode();
        } catch (java.io.IOException e) {
            System.err.println("Error encoding StartRoundMsg: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // Send to all nodes via their addresses
        for (Map.Entry<Integer, Address> entry : addressTable.entrySet()) {
            Address nodeAddress = entry.getValue();
            supervisorCommunication.sendMessage(nodeAddress, encodedMsg);
        }

        System.out.println("Round " + currentRound + " triggered for all " + nodes.size() + " nodes");

        // Check convergence after a delay to allow messages to be processed
        // Schedule convergence check after 80% of round interval (to give time for
        // messages to be processed)
        if (roundScheduler != null && !roundScheduler.isShutdown()) {
            final double interval = this.roundInterval;
            long delayMs = (long) (interval * 1000 * 0.8); // 80% of round interval
            roundScheduler.schedule(() -> {
                checkConvergenceAndStop();
            }, delayMs, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Check if convergence criteria are met and stop all threads if so
     */
    private void checkConvergenceAndStop() {
        if (hasConverged()) {
            // Count infected nodes (with spreadable messages) and susceptible nodes
            // (without spreadable messages)
            int infectedNodes = 0;
            int susceptibleNodes = 0;

            for (Node node : nodes.values()) {
                List<epidemic_core.message.node_to_node.spread.SpreadMsg> storedMessages = node.getAllStoredMessages();

                boolean hasSpreadableMessage = false;
                if (storedMessages != null && !storedMessages.isEmpty()) {
                    // For Gossip nodes, check if there are any messages that are NOT removed
                    if (node instanceof epidemic_core.node.GossipNode) {
                        epidemic_core.node.GossipNode gossipNode = (epidemic_core.node.GossipNode) node;
                        for (epidemic_core.message.node_to_node.spread.SpreadMsg msg : storedMessages) {
                            if (!gossipNode.isMessageRemoved(msg.getId())) {
                                hasSpreadableMessage = true;
                                break;
                            }
                        }
                    } else {
                        // For non-Gossip nodes, all stored messages can be spread
                        hasSpreadableMessage = true;
                    }
                }

                if (hasSpreadableMessage) {
                    infectedNodes++;
                } else {
                    susceptibleNodes++; // Susceptible = no spreadable messages
                }
            }

            // Stop round scheduling
            stopRoundScheduling();

            // Stop all nodes
            stopAllNodes();

            // Print convergence statistics
            System.out.println("\n========================================");
            System.out.println("CONVERGENCE ACHIEVED!");
            System.out.println("========================================");
            System.out.println("Final Round: " + currentRound);
            System.out.println("Infected Nodes: " + infectedNodes);
            System.out.println("Residual (Susceptible): " + susceptibleNodes);
            System.out.println("Total Nodes: " + numberOfNodes);
            System.out.println("========================================\n");
        }
    }

    /**
     * Stop all node threads
     */
    private void stopAllNodes() {
        System.out.println("Stopping all node threads...");
        for (Node node : nodes.values()) {
            if (node instanceof AntiEntropyPullNode) {
                ((AntiEntropyPullNode) node).stopRunning();
            } else if (node instanceof AntiEntropyPushNode) {
                ((AntiEntropyPushNode) node).stopRunning();
            } else if (node instanceof BlindCoinPushNode) {
                ((BlindCoinPushNode) node).stopRunning();
            } else if (node instanceof BlindCoinPullNode) {
                ((BlindCoinPullNode) node).stopRunning();
            } else if (node instanceof BlindCoinPushPullNode) {
                ((BlindCoinPushPullNode) node).stopRunning();
            } else if (node instanceof FeedbackCoinPushNode) {
                ((FeedbackCoinPushNode) node).stopRunning();
            } else if (node instanceof FeedbackCoinPullNode) {
                ((FeedbackCoinPullNode) node).stopRunning();
            } else if (node instanceof FeedbackCoinPushPullNode) {
                ((FeedbackCoinPushPullNode) node).stopRunning();
            } else if (node instanceof AntiEntropyPushPullNode) {
                ((AntiEntropyPushPullNode) node).stopRunning();
            }
        }
        System.out.println("All node threads stopped.");
    }

    /**
     * Start automatic round scheduling
     * 
     * @param roundInterval Interval between rounds in seconds
     */
    public void startRoundScheduling(double roundInterval) {
        if (roundScheduler != null && !roundScheduler.isShutdown()) {
            System.out.println("Round scheduling already started");
            return;
        }

        this.roundInterval = roundInterval;
        roundScheduler = Executors.newSingleThreadScheduledExecutor();
        roundScheduler.scheduleAtFixedRate(
                this::triggerPullRound,
                0,
                (long) (roundInterval * 1000),
                TimeUnit.MILLISECONDS);
        System.out.println("Round scheduling started with interval of " + roundInterval + " seconds");
    }

    /**
     * Stop automatic round scheduling
     */
    public void stopRoundScheduling() {
        if (roundScheduler != null && !roundScheduler.isShutdown()) {
            roundScheduler.shutdown();
            System.out.println("Round scheduling stopped");
        }
    }

    /**
     * Get current round number
     */
    public int getCurrentRound() {
        return currentRound;
    }

    /**
     * Get all unique subjects from infection history
     * 
     * @return Set of unique subjects
     */
    public Set<String> getAllSubjects() {
        Set<String> subjects = new HashSet<>();
        for (List<InfectionRecord> records : infectionHistory.values()) {
            for (InfectionRecord record : records) {
                subjects.add(record.getSubject());
            }
        }
        return subjects;
    }

    /**
     * Get all unique subject+sourceId combinations from infection history
     * 
     * @return Set of strings in format "subject:sourceId"
     */
    public Set<String> getAllSubjectSourceCombinations() {
        Set<String> combinations = new HashSet<>();
        for (List<InfectionRecord> records : infectionHistory.values()) {
            for (InfectionRecord record : records) {
                String combo = record.getSubject() + ":" + record.getSourceId();
                combinations.add(combo);
            }
        }
        return combinations;
    }

    /**
     * Generate and display a chart showing infections over rounds
     * 
     * @param subject  Subject to filter by (null = all subjects)
     * @param sourceId Source ID to filter by (null = all sources)
     */
    public void generateInfectionChart(String subject, Integer sourceId) {
        // Create dataset
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        // Group infections by round, filtered by subject if specified
        Map<Integer, Set<Integer>> uniqueInfectionsPerRound = new TreeMap<>();

        // Count unique infected nodes per round (cumulative) for the specified subject
        Set<Integer> cumulativeInfectedNodes = new HashSet<>();

        // Track first infection per node per subject
        Map<Integer, Integer> firstInfectionRound = new HashMap<>(); // nodeId -> first round infected

        // First pass: find first infection round for each node (filtered by subject and
        // sourceId)
        for (Map.Entry<Integer, List<InfectionRecord>> entry : infectionHistory.entrySet()) {
            int nodeId = entry.getKey();
            for (InfectionRecord record : entry.getValue()) {
                // Filter by subject and sourceId if specified
                boolean matchesSubject = (subject == null || record.getSubject().equals(subject));
                boolean matchesSource = (sourceId == null || record.getSourceId() == sourceId);

                if (matchesSubject && matchesSource) {
                    int round = record.getRound();
                    if (!firstInfectionRound.containsKey(nodeId) || round < firstInfectionRound.get(nodeId)) {
                        firstInfectionRound.put(nodeId, round);
                    }
                    // If filtering by specific subject+source, break after first match
                    // If null (all subjects/sources), continue to find earliest infection
                    if (subject != null && sourceId != null) {
                        break;
                    }
                }
            }
        }

        // Process all rounds up to current round
        for (int round = 0; round <= currentRound; round++) {
            Set<Integer> roundInfections = new HashSet<>();

            // Count nodes that were first infected in this round
            for (Map.Entry<Integer, Integer> entry : firstInfectionRound.entrySet()) {
                int nodeId = entry.getKey();
                int firstRound = entry.getValue();
                if (firstRound == round) {
                    roundInfections.add(nodeId);
                }
            }

            cumulativeInfectedNodes.addAll(roundInfections);
            uniqueInfectionsPerRound.put(round, new HashSet<>(cumulativeInfectedNodes));
        }

        // Add initial point at round 0 (source nodes)
        int initialInfected = uniqueInfectionsPerRound.getOrDefault(0, Collections.emptySet()).size();
        dataset.addValue(initialInfected, "Cumulative Infections", "0");

        // Add data to dataset
        int maxRound = uniqueInfectionsPerRound.isEmpty() ? 0 : Collections.max(uniqueInfectionsPerRound.keySet());
        for (int round = 1; round <= maxRound; round++) {
            int cumulativeCount = uniqueInfectionsPerRound.getOrDefault(round, Collections.emptySet()).size();

            // Stop adding data when all nodes are infected (chart shows infections, but
            // auto-update stops when all are infected and/or removed)
            if (cumulativeCount >= numberOfNodes) {
                cumulativeCount = numberOfNodes;
                dataset.addValue(cumulativeCount, "Cumulative Infections", String.valueOf(round));
                break;
            }

            dataset.addValue(cumulativeCount, "Cumulative Infections", String.valueOf(round));
        }

        // If we haven't reached all nodes, add final point
        if (!uniqueInfectionsPerRound.isEmpty()) {
            int lastRound = Collections.max(uniqueInfectionsPerRound.keySet());
            int finalCount = uniqueInfectionsPerRound.get(lastRound).size();
            if (finalCount < numberOfNodes) {
                dataset.addValue(finalCount, "Cumulative Infections", String.valueOf(lastRound));
            }
        }

        // Create chart title based on subject and sourceId filter
        String chartTitle;
        if (subject == null && sourceId == null) {
            chartTitle = "Infections Over Rounds (All Subjects & Sources)";
        } else if (subject != null && sourceId != null) {
            chartTitle = "Infections Over Rounds - Subject: " + subject + ", Source: " + sourceId;
        } else if (subject != null) {
            chartTitle = "Infections Over Rounds - Subject: " + subject + " (All Sources)";
        } else {
            chartTitle = "Infections Over Rounds - Source: " + sourceId + " (All Subjects)";
        }

        // Create chart
        JFreeChart chart = ChartFactory.createLineChart(
                chartTitle,
                "Round",
                "Cumulative Infections",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false);

        // Display chart
        if (chartFrame == null || !chartFrame.isVisible()) {
            chartFrame = new ChartFrame("Infection Chart", chart);
            chartFrame.pack();
            chartFrame.setVisible(true);
            currentChart = chart;
        } else {
            // Update existing chart
            currentChart = chart;
            chartFrame.getChartPanel().setChart(chart);
        }

        int totalInfected = uniqueInfectionsPerRound.isEmpty() ? 0
                : uniqueInfectionsPerRound.get(Collections.max(uniqueInfectionsPerRound.keySet())).size();
        String filterInfo;
        if (subject == null && sourceId == null) {
            filterInfo = "all subjects and sources";
        } else if (subject != null && sourceId != null) {
            filterInfo = "subject '" + subject + "' from source " + sourceId;
        } else if (subject != null) {
            filterInfo = "subject '" + subject + "' (all sources)";
        } else {
            filterInfo = "source " + sourceId + " (all subjects)";
        }
        System.out.println("Chart displayed/updated for " + filterInfo + ". Total infections: " + totalInfected + "/"
                + numberOfNodes + " (Round " + currentRound + ")");

        // Check convergence criteria: all nodes infected OR no nodes infected
        boolean converged = hasConverged();

        // Start auto-update if not already running and not converged
        if (!chartAutoUpdate && !converged) {
            startChartAutoUpdate();
        } else if (converged && chartAutoUpdate) {
            stopChartAutoUpdate();
            if (totalInfected == numberOfNodes) {
                System.out.println("All nodes infected! Chart auto-update stopped.");
            } else if (totalInfected == 0) {
                System.out.println("No nodes infected! Chart auto-update stopped.");
            }
        }
    }

    /**
     * Generate and display a chart showing infections over rounds (all subjects and
     * sources)
     */
    public void generateInfectionChart() {
        generateInfectionChart(null, null);
    }

    /**
     * Generate and display a chart showing infections over rounds for a specific
     * subject
     * 
     * @param subject Subject to filter by
     */
    public void generateInfectionChart(String subject) {
        generateInfectionChart(subject, null);
    }

    /**
     * Start automatic chart updates
     */
    private void startChartAutoUpdate() {
        chartAutoUpdate = true;
        ScheduledExecutorService chartUpdater = Executors.newSingleThreadScheduledExecutor();
        chartUpdater.scheduleAtFixedRate(() -> {
            if (!chartAutoUpdate) {
                chartUpdater.shutdown();
                return;
            }

            // Stop if convergence criteria met: all nodes infected OR no nodes infected
            if (hasConverged()) {
                chartAutoUpdate = false;
                generateInfectionChart(); // Final update
                chartUpdater.shutdown();
                int infectedCount = uniqueInfectedNodes.size();
                if (infectedCount == numberOfNodes) {
                    System.out.println("All nodes infected! Chart auto-update stopped.");
                } else if (infectedCount == 0) {
                    System.out.println("No nodes infected! Chart auto-update stopped.");
                }
                return;
            }

            // Update chart
            generateInfectionChart();
        }, 1, 1, TimeUnit.SECONDS); // Update every second
    }

    /**
     * Stop automatic chart updates
     */
    private void stopChartAutoUpdate() {
        chartAutoUpdate = false;
    }

    /**
     * Shutdown the supervisor and all nodes
     */
    public void shutdown() {
        System.out.println("Shutting down supervisor...");
        stopRoundScheduling();
        supervisorCommunication.closeSocket();
        System.out.println("Supervisor shut down.");
    }

    /**
     * Get address table (for external use)
     */
    public Map<Integer, Address> getAddressTable() {
        return new HashMap<>(addressTable);
    }

    /**
     * Get supervisor address
     */
    public Address getSupervisorAddress() {
        return supervisorAddress;
    }

    /**
     * Get all node IDs (for GUI)
     */
    public List<Integer> getAllNodeIds() {
        List<Integer> allNodeIds = new ArrayList<>();
        for (int i = 1; i <= numberOfNodes; i++) {
            allNodeIds.add(i);
        }
        return allNodeIds;
    }

    /**
     * Get number of nodes
     */
    public int getNumberOfNodes() {
        return numberOfNodes;
    }

    /**
     * Check if a node is a source (for GUI, based on messages received)
     */
    public boolean isNodeSource(int nodeId) {
        return sourceNodes.containsKey(nodeId);
    }

    /**
     * Get subject for a source node (for GUI, based on messages received)
     */
    public String getSourceSubject(int nodeId) {
        return sourceNodes.get(nodeId);
    }

    /**
     * Get all infection records for a node (for GUI, based on messages received)
     */
    public List<InfectionRecord> getNodeInfections(int nodeId) {
        return getInfectionHistory(nodeId);
    }
}
