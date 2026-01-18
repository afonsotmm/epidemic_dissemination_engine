package epidemic_core.node;

import epidemic_core.message.common.MessageDispatcher;
import epidemic_core.message.node_to_supervisor.hello.HelloMsg;
import epidemic_core.message.supervisor_to_node.kill_node.KillNodeMsg;
import epidemic_core.message.supervisor_to_node.start_node.StartNodeMsg;
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
import general.communication.implementation.TcpCommunication;
import general.communication.implementation.UdpCommunication;
import general.communication.utils.Address;

import java.io.IOException;
import java.util.Random;

/**
 * DistributedNodeStub: Manages node lifecycle in distributed deployment mode.
 * 
 * States:
 * - WAVING: Sends HelloMsg via UDP broadcast every 2s, listens for StartNodeMsg
 * - WORKING: Normal node operation after receiving StartNodeMsg
 * - Returns to WAVING when receiving KillNodeMsg
 */
public class DistributedNodeStub {

    private static final int HELLO_INTERVAL_MIN_MS = 1000; // Minimum 1 second
    private static final int HELLO_INTERVAL_MAX_MS = 5000; // Maximum 5 seconds
    private static final int SUPERVISOR_DISCOVERY_PORT = 7000; // Default supervisor UDP port for discovery
    private static final Random random = new Random(); // Random generator for Hello interval
    
    private enum State {
        WAVING,  // Sending HelloMsg, waiting for StartNodeMsg
        WORKING  // Node initialized and running
    }
    
    private volatile State state = State.WAVING;
    private final int helloIntervalMs; // Random interval between 1-5 seconds (unique per node instance)
    private UdpCommunication udpCommunication;
    private TcpCommunication tcpCommunication; // TCP server to receive StartNodeMsg and KillNodeMsg
    private Address myUdpAddress; // This node's UDP address (IP + port) - for receiving HelloMsg
    private Address myTcpAddress; // This node's TCP address (IP + portTCP) - sent in HelloMsg
    private Node actualNode; // The actual Node instance created after StartNodeMsg
    private Thread nodeThread; // Thread running the actual node
    private Thread wavingThread; // Thread for sending HelloMsg periodically
    private Thread tcpListeningThread; // Thread for listening TCP messages
    
    // Constructor: Initialize with minimal address information
    // The node doesn't know its ID yet, only its address
    // TCP port is UDP port + 1 (e.g., UDP 8000 -> TCP 8001)
    public DistributedNodeStub(String ip, int udpPort) {
        // Generate random Hello interval between 1-5 seconds (unique per node instance)
        this.helloIntervalMs = HELLO_INTERVAL_MIN_MS + random.nextInt(HELLO_INTERVAL_MAX_MS - HELLO_INTERVAL_MIN_MS + 1);
        
        this.myUdpAddress = new Address(ip, udpPort);
        int tcpPort = udpPort + 1; // TCP port is UDP port + 1
        this.myTcpAddress = new Address(ip, tcpPort);
        
        this.udpCommunication = new UdpCommunication();
        this.tcpCommunication = new TcpCommunication();
        
        // Setup UDP socket to listen (for broadcasting HelloMsg)
        udpCommunication.setupSocket(myUdpAddress);
        
        // Setup TCP socket to listen for StartNodeMsg and KillNodeMsg (on different port)
        tcpCommunication.setupSocket(myTcpAddress);
        
        // Start Waving mode
        startWaving();
    }
    
    /**
     * Start Waving mode: Send HelloMsg via UDP broadcast with random intervals (1-5s)
     */
    private void startWaving() {
        state = State.WAVING;
        
        // Start thread to send HelloMsg periodically
        wavingThread = Thread.startVirtualThread(() -> {
            // Add random initial delay (0-5 seconds) to spread out the first HelloMsg from all nodes
            // This prevents all nodes from sending their first HelloMsg simultaneously
            int initialDelayMs = random.nextInt(HELLO_INTERVAL_MAX_MS + 1);
            try {
                Thread.sleep(initialDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            
            while (state == State.WAVING) {
                try {
                    // Create HelloMsg with both TCP and UDP addresses
                    // TCP: for supervisor-to-node messages (StartNodeMsg, KillNodeMsg)
                    // UDP: for node-to-node communication and StartRoundMsg
                    HelloMsg helloMsg = new HelloMsg(
                        epidemic_core.message.common.Direction.node_to_supervisor.toString(),
                        epidemic_core.message.node_to_supervisor.NodeToSupervisorMessageType.hello.toString(),
                        myTcpAddress.getIp() + ":" + myTcpAddress.getPort(),
                        myUdpAddress.getIp() + ":" + myUdpAddress.getPort()
                    );
                    
                    String encodedHello = helloMsg.encode();
                    
                    // Send via UDP broadcast to supervisor discovery port
                    ((UdpCommunication) udpCommunication).sendBroadcastMessage(SUPERVISOR_DISCOVERY_PORT, encodedHello);
                    
                    // Wait random interval (1-5 seconds) before next HelloMsg (unique per node)
                    Thread.sleep(helloIntervalMs);
                } catch (IOException e) {
                    System.err.println("[DistributedNodeStub] Error encoding/sending HelloMsg: " + e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        
        // Start thread to listen for UDP messages (KillNodeMsg)
        Thread.startVirtualThread(this::udpListeningLoop);
        
        // Start thread to listen for TCP messages (StartNodeMsg - large message)
        tcpListeningThread = Thread.startVirtualThread(this::tcpListeningLoop);
    }
    
    /**
     * UDP listening loop: Not used for receiving messages from supervisor
     * (StartNodeMsg and KillNodeMsg are received via TCP)
     */
    private void udpListeningLoop() {
        // UDP is only used for sending HelloMsg broadcast
        // Supervisor messages (StartNodeMsg, KillNodeMsg) come via TCP
        while (true) {
            try {
                Thread.sleep(1000); // Just keep thread alive
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * TCP listening loop: Process StartNodeMsg and KillNodeMsg (both via TCP)
     */
    private void tcpListeningLoop() {
        while (state == State.WAVING || state == State.WORKING) {
            String receivedMessage = tcpCommunication.receiveMessage();
            if (receivedMessage == null) {
                try {
                    Thread.sleep(10); // Small sleep to avoid busy-waiting
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }
            
            try {
                Object decodedMsg = MessageDispatcher.decode(receivedMessage);
                
                if (decodedMsg instanceof StartNodeMsg && state == State.WAVING) {
                    handleStartNodeMsg((StartNodeMsg) decodedMsg);
                } else if (decodedMsg instanceof KillNodeMsg && state == State.WORKING) {
                    handleKillNodeMsg();
                }
            } catch (Exception e) {
                System.err.println("[DistributedNodeStub] Error processing TCP message: " + e.getMessage());
            }
        }
    }
    
    /**
     * Handle StartNodeMsg: Initialize the actual Node and switch to WORKING state
     */
    private void handleStartNodeMsg(StartNodeMsg msg) {
        try {
            System.out.println("[DistributedNodeStub] Received StartNodeMsg for node ID: " + msg.getNodeId());
            
            // Stop waving
            state = State.WORKING;
            if (wavingThread != null) {
                wavingThread.interrupt();
            }
            
            // Parse StartNodeMsg fields
            int nodeId = msg.getNodeId();
            Address supervisorTcpAddress = msg.getSupervisorTcpAddressAsAddress();
            java.util.List<Integer> neighbors = msg.getNeighbors();
            java.util.Map<Integer, Address> nodeToAddressTable = msg.getNodeToAddressTableAsMap();
            java.util.List<epidemic_core.message.common.MessageTopic> subscribedTopics = msg.getSubscribedTopicsAsList();
            String assignedSubjectAsSource = msg.getAssignedSubjectAsSource();
            String mode = msg.getMode();
            String protocol = msg.getProtocol();
            Double k = msg.getK();
            
            // Create the actual Node based on mode and protocol
            Node node = createNode(nodeId, neighbors, assignedSubjectAsSource, 
                                  nodeToAddressTable, subscribedTopics, supervisorTcpAddress,
                                  mode, protocol, k);
            
            this.actualNode = node;
            
            // Configure TCP communication for supervisor (for InfectionUpdateMsg and RemotionUpdateMsg)
            // supervisorAddress is the TCP address of supervisor in distributed mode
            TcpCommunication tcpComm = new TcpCommunication();
            node.supervisorTcpCommunication = tcpComm;
            // Note: TcpCommunication doesn't need setupSocket for client mode - it creates socket per message
            
            // Start the node (it will start its worker threads)
            if (node instanceof AntiEntropyPullNode n) n.startRunning();
            else if (node instanceof AntiEntropyPushNode n) n.startRunning();
            else if (node instanceof AntiEntropyPushPullNode n) n.startRunning();
            else if (node instanceof GossipPullNode n) n.startRunning();
            else if (node instanceof GossipPushNode n) n.startRunning();
            else if (node instanceof GossipPushPullNode n) n.startRunning();
            
            System.out.println("[DistributedNodeStub] Node " + nodeId + " initialized and started in WORKING mode with TCP communication to supervisor");
            
        } catch (Exception e) {
            System.err.println("[DistributedNodeStub] Error initializing node from StartNodeMsg: " + e.getMessage());
            e.printStackTrace();
            // Return to WAVING state on error
            state = State.WAVING;
            startWaving();
        }
    }
    
    /**
     * Create the actual Node instance based on mode and protocol
     */
    private Node createNode(int nodeId,
                           java.util.List<Integer> neighbors,
                           String assignedSubjectAsSource,
                           java.util.Map<Integer, Address> nodeToAddressTable,
                           java.util.List<epidemic_core.message.common.MessageTopic> subscribedTopics,
                           Address supervisorAddress,
                           String mode,
                           String protocol,
                           Double k) {
        
        NodeMode nodeMode = NodeMode.fromString(mode);
        String protocolLower = protocol != null ? protocol.toLowerCase() : "anti_entropy";
        double kValue = k != null ? k : 2.0;
        
        switch (nodeMode) {
            case PULL:
                return switch (protocolLower) {
                    case "anti_entropy" -> new AntiEntropyPullNode(nodeId, neighbors, assignedSubjectAsSource, 
                                                                   nodeToAddressTable, subscribedTopics, supervisorAddress, udpCommunication);
                    case "gossip_feedback_coin", "feedback_coin" -> new FeedbackCoinPullNode(nodeId, neighbors, assignedSubjectAsSource, 
                                                                                             nodeToAddressTable, subscribedTopics, supervisorAddress, kValue, udpCommunication);
                    case "gossip_blind_coin", "blind_coin" -> new BlindCoinPullNode(nodeId, neighbors, assignedSubjectAsSource, 
                                                                                    nodeToAddressTable, subscribedTopics, supervisorAddress, kValue, udpCommunication);
                    default -> throw new IllegalArgumentException("Invalid protocol for PULL mode: " + protocol);
                };
            
            case PUSH:
                return switch (protocolLower) {
                    case "anti_entropy" -> new AntiEntropyPushNode(nodeId, neighbors, assignedSubjectAsSource, 
                                                                   nodeToAddressTable, subscribedTopics, supervisorAddress, udpCommunication);
                    case "gossip_feedback_coin", "feedback_coin" -> new FeedbackCoinPushNode(nodeId, neighbors, assignedSubjectAsSource, 
                                                                                             nodeToAddressTable, subscribedTopics, supervisorAddress, kValue, udpCommunication);
                    case "gossip_blind_coin", "blind_coin" -> new BlindCoinPushNode(nodeId, neighbors, assignedSubjectAsSource, 
                                                                                    nodeToAddressTable, subscribedTopics, supervisorAddress, kValue, udpCommunication);
                    default -> throw new IllegalArgumentException("Invalid protocol for PUSH mode: " + protocol);
                };
            
            case PUSHPULL:
                return switch (protocolLower) {
                    case "anti_entropy" -> new AntiEntropyPushPullNode(nodeId, neighbors, assignedSubjectAsSource, 
                                                                       nodeToAddressTable, subscribedTopics, supervisorAddress, udpCommunication);
                    case "gossip_feedback_coin", "feedback_coin" -> new FeedbackCoinPushPullNode(nodeId, neighbors, assignedSubjectAsSource, 
                                                                                                 nodeToAddressTable, subscribedTopics, supervisorAddress, kValue, udpCommunication);
                    case "gossip_blind_coin", "blind_coin" -> new BlindCoinPushPullNode(nodeId, neighbors, assignedSubjectAsSource, 
                                                                                        nodeToAddressTable, subscribedTopics, supervisorAddress, kValue, udpCommunication);
                    default -> throw new IllegalArgumentException("Invalid protocol for PUSHPULL mode: " + protocol);
                };
            
            default:
                throw new IllegalArgumentException("Invalid node mode: " + mode);
        }
    }
    
    /**
     * Handle KillNodeMsg: Stop the node and return to WAVING state
     */
    private void handleKillNodeMsg() {
        System.out.println("[DistributedNodeStub] Received KillNodeMsg - returning to WAVING state");
        
        // Stop the actual node
        if (actualNode != null) {
            actualNode.stop();
            actualNode = null;
        }
        
        if (nodeThread != null) {
            nodeThread.interrupt();
            nodeThread = null;
        }
        
        // Return to WAVING state
        startWaving();
    }
    
    /**
     * Get the actual Node instance (null if still in WAVING state)
     */
    public Node getActualNode() {
        return actualNode;
    }
    
    /**
     * Check if node is in WORKING state
     */
    public boolean isWorking() {
        return state == State.WORKING;
    }
    
    /**
     * Stop the stub and clean up resources
     */
    public void stop() {
        if (wavingThread != null) {
            wavingThread.interrupt();
        }
        if (tcpListeningThread != null) {
            tcpListeningThread.interrupt();
        }
        if (actualNode != null) {
            actualNode.stop();
        }
        if (udpCommunication != null) {
            udpCommunication.closeSocket();
        }
        if (tcpCommunication != null) {
            tcpCommunication.closeSocket();
        }
    }
    
    /**
     * Main method for distributed deployment mode
     * Each node runs independently with its own IP and port
     * 
     * Usage: java DistributedNodeStub <ip> <port>
     * Example: java DistributedNodeStub 127.0.0.1 8000
     * 
     * If no arguments provided, uses default: 127.0.0.1:8000
     */
    public static void main(String[] args) {
        String ip;
        int port;
        
        // Parse command line arguments
        if (args.length >= 2) {
            ip = args[0];
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[1] + ". Using default port 8000.");
                port = 8000;
            }
        } else {
            // Default values
            ip = "127.0.0.1";
            port = 8000;
            System.out.println("No arguments provided. Using default: " + ip + ":" + port);
            System.out.println("Usage: java DistributedNodeStub <ip> <port>");
        }
        
        System.out.println("================================================");
        System.out.println("Starting DistributedNodeStub");
        System.out.println("  IP: " + ip);
        System.out.println("  Port: " + port);
        System.out.println("  Mode: WAVING (waiting for StartNodeMsg from supervisor)");
        System.out.println("================================================");
        
        // Create DistributedNodeStub - it will start in WAVING mode
        DistributedNodeStub stub = new DistributedNodeStub(ip, port);
        
        // Keep the main thread alive
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("DistributedNodeStub interrupted. Shutting down...");
            stub.stop();
            Thread.currentThread().interrupt();
        }
    }
}
