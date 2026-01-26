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

    private static final int HELLO_INTERVAL_MIN_MS = 1000;
    private static final int HELLO_INTERVAL_MAX_MS = 5000;
    private static final int SUPERVISOR_DISCOVERY_PORT = 7000;
    private static final Random random = new Random();

    private enum State {
        WAVING,
        WORKING
    }
    
    private volatile State state = State.WAVING;
    private final int helloIntervalMs;
    private UdpCommunication udpCommunication;
    private TcpCommunication tcpCommunication;
    private Address myUdpAddress;
    private Address myTcpAddress;
    private Node actualNode;
    private Thread nodeThread;
    private Thread wavingThread;
    private Thread tcpListeningThread;

    public DistributedNodeStub(String ip, int udpPort) {
        this.helloIntervalMs = HELLO_INTERVAL_MIN_MS + random.nextInt(HELLO_INTERVAL_MAX_MS - HELLO_INTERVAL_MIN_MS + 1);
        
        this.myUdpAddress = new Address(ip, udpPort);
        int tcpPort = udpPort + 1;
        this.myTcpAddress = new Address(ip, tcpPort);
        
        this.udpCommunication = new UdpCommunication();
        this.tcpCommunication = new TcpCommunication();

        udpCommunication.setupSocket(myUdpAddress);

        tcpCommunication.setupSocket(myTcpAddress);

        startWaving();
    }

    private void startWaving() {
        state = State.WAVING;

        wavingThread = Thread.startVirtualThread(() -> {
            int initialDelayMs = random.nextInt(HELLO_INTERVAL_MAX_MS + 1);
            try {
                Thread.sleep(initialDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            
            while (state == State.WAVING) {
                try {
                    HelloMsg helloMsg = new HelloMsg(
                        epidemic_core.message.common.Direction.node_to_supervisor.toString(),
                        epidemic_core.message.node_to_supervisor.NodeToSupervisorMessageType.hello.toString(),
                        myTcpAddress.getIp() + ":" + myTcpAddress.getPort(),
                        myUdpAddress.getIp() + ":" + myUdpAddress.getPort()
                    );
                    
                    String encodedHello = helloMsg.encode();

                    ((UdpCommunication) udpCommunication).sendBroadcastMessage(SUPERVISOR_DISCOVERY_PORT, encodedHello);

                    Thread.sleep(helloIntervalMs);
                } catch (IOException e) {
                    System.err.println("[DistributedNodeStub] Error encoding/sending HelloMsg: " + e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        Thread.startVirtualThread(this::udpListeningLoop);

        tcpListeningThread = Thread.startVirtualThread(this::tcpListeningLoop);
    }

    private void udpListeningLoop() {
        while (true) {
            try {
                Thread.sleep(1000); // Just keep thread alive
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void tcpListeningLoop() {
        while (state == State.WAVING || state == State.WORKING) {
            String receivedMessage = tcpCommunication.receiveMessage();
            if (receivedMessage == null) {
                try {
                    Thread.sleep(10);
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

    private void handleStartNodeMsg(StartNodeMsg msg) {
        try {
            System.out.println("[DistributedNodeStub] Received StartNodeMsg for node ID: " + msg.getNodeId());
            
            // Stop waving
            state = State.WORKING;
            if (wavingThread != null) {
                wavingThread.interrupt();
            }

            int nodeId = msg.getNodeId();
            Address supervisorTcpAddress = msg.getSupervisorTcpAddressAsAddress();
            java.util.List<Integer> neighbors = msg.getNeighbors();
            java.util.Map<Integer, Address> nodeToAddressTable = msg.getNodeToAddressTableAsMap();
            java.util.List<epidemic_core.message.common.MessageTopic> subscribedTopics = msg.getSubscribedTopicsAsList();
            String assignedSubjectAsSource = msg.getAssignedSubjectAsSource();
            String mode = msg.getMode();
            String protocol = msg.getProtocol();
            Double k = msg.getK();

            Node node = createNode(nodeId, neighbors, assignedSubjectAsSource, 
                                  nodeToAddressTable, subscribedTopics, supervisorTcpAddress,
                                  mode, protocol, k);
            
            this.actualNode = node;

            TcpCommunication tcpComm = new TcpCommunication();
            node.supervisorTcpCommunication = tcpComm;

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

            state = State.WAVING;
            startWaving();
        }
    }

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

    private void handleKillNodeMsg() {
        System.out.println("[DistributedNodeStub] Received KillNodeMsg - returning to WAVING state");

        if (actualNode != null) {
            actualNode.stop();
            actualNode = null;
        }
        
        if (nodeThread != null) {
            nodeThread.interrupt();
            nodeThread = null;
        }

        startWaving();
    }

    public Node getActualNode() {
        return actualNode;
    }

    public boolean isWorking() {
        return state == State.WORKING;
    }

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

    public static void main(String[] args) {
        String ip;
        int port;

        if (args.length >= 2) {
            ip = args[0];
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[1] + ". Using default port 8000.");
                port = 8000;
            }
        } else {
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

        DistributedNodeStub stub = new DistributedNodeStub(ip, port);

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("DistributedNodeStub interrupted. Shutting down...");
            stub.stop();
            Thread.currentThread().interrupt();
        }
    }
}
