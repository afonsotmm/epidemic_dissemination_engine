package supervisor;

import epidemic_core.message.supervisor_to_node.start_round.StartRoundMsg;
import epidemic_core.message.supervisor_to_ui.structural_infos.StructuralInfosMsg;
import epidemic_core.message.ui_to_supervisor.end_system.EndMsg;
import epidemic_core.message.ui_to_supervisor.start_system.StartMsg;
import general.communication.Communication;
import general.communication.implementation.UdpCommunication;
import general.communication.utils.Address;
import supervisor.communication.Dispatcher;
import supervisor.communication.Listener;
import supervisor.communication.Worker;
import supervisor.network_emulation.NetworkEmulator;
import supervisor.ui.SupervisorGui;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Orchestrator responsible for managing communication infrastructure
 * and controlling network topology initialization and lifecycle
 */

public class Supervisor{

    private Communication communication;

    private Listener listener;
    private Dispatcher dispatcher;
    private Worker worker;
    private NetworkEmulator system;

    private BlockingQueue<String> msgsQueue;
    private BlockingQueue<String> nodeQueue;
    private BlockingQueue<String> uiQueue;

    private Thread listenerThread;
    private Thread dispatcherThread;
    private Thread workerThread;
    private Thread startRoundThread;

    private StartMsg startMessage;
    private volatile boolean isNetworkRunning = false;
    private final long roundInterval = 2;
    private int supervisorPort = 7000; // Default port, updated in initialize()
    private SupervisorGui gui;
    private int currentRound = 0;

    public Supervisor(){
        // buffers:
        this.msgsQueue = new LinkedBlockingQueue<>();
        this.nodeQueue = new LinkedBlockingQueue<>();
        this.uiQueue = new LinkedBlockingQueue<>();

        this.communication = new UdpCommunication();
        this.listener = new Listener(this, msgsQueue);
        this.dispatcher = new Dispatcher(msgsQueue, nodeQueue, uiQueue);
        this.worker = new Worker(this, nodeQueue, uiQueue);
    }

    public Communication getCommunication() {
        return communication;
    }

    public Address getUiAddress() {
        return startMessage != null ? startMessage.getAddr() : null;
    }

    // initialize topology and nodes
    public void startNetwork(StartMsg startMessage){
        this.startMessage = startMessage;
        
        // Initialize local GUI
        gui = new SupervisorGui(startMessage.getN());
        
        // Get supervisor address (where it's listening)
        Address supervisorAddress = new Address("127.0.0.1", supervisorPort);
        system = new NetworkEmulator(startMessage.getN(), startMessage.getSourceNodes(), startMessage.getTopology(), startMessage.getProtocol(), startMessage.getMode(), supervisorAddress);
        
        // Initialize network (this waits for all nodes to be created)
        system.initializeNetwork();
        
        // Send structural information to UI
        sendStructuralInfosToUi();
        
        // Wait additional time for sockets to fully initialize (like old supervisor)
        try {
            System.out.println("Waiting additional time for sockets to initialize...");
            Thread.sleep(2000); // 2 seconds delay like old supervisor
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Initialize round 0 and register source nodes (like old supervisor)
        currentRound = 0;
        if (gui != null) {
            // Register source nodes as infected in round 0
            registerSourceNodesInRound0();
        }
        
        // Start sending start_round messages
        isNetworkRunning = true;
        startRoundThread = Thread.startVirtualThread(this::sendStartRoundPeriodically);
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
    
    // Send structural information to UI
    private void sendStructuralInfosToUi() {
        try {
            supervisor.network_emulation.neighbors_and_subject.StructuralInfosMatrix matrix = system.getStructuralInfosMatrix();
            StructuralInfosMsg msg = StructuralInfosMsg.fromStructuralInfosMatrix(matrix);
            String encodedMsg = msg.encode();
            
            // Check size before sending
            byte[] messageBytes = encodedMsg.getBytes();
            int practicalMaxSize = 60000; // Safe UDP limit
            
            if (messageBytes.length > practicalMaxSize) {
                System.out.println("Note: StructuralInfosMsg is too large (" + messageBytes.length + " bytes) for UDP. " +
                                 "Skipping send to external UI (local GUI still works).");
                return;
            }
            
            sendToUi(encodedMsg);
        } catch (Exception e) {
            System.err.println("Error sending structural infos to UI: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendStartRoundPeriodically() {
        try {
            while (isNetworkRunning && system != null) {
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
                
                Map<Integer, Address> nodeAddresses = system.getNodeAddresses();
                int nodesCount = nodeAddresses.size();
                for (Map.Entry<Integer, Address> entry : nodeAddresses.entrySet()) {
                    Address nodeAddress = entry.getValue();
                    if (nodeAddress != null) {
                        communication.sendMessage(nodeAddress, encodedMsg);
                    }
                }
                
                System.out.println("Round " + currentRound + " triggered for all " + nodesCount + " nodes");
                
                Thread.sleep(roundInterval * 1000); // Sleep for 2 seconds
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
    
    
    // Send message to UI
    public void sendToUi(String encodedMessage) {
        Address uiAddress = getUiAddress();
        if (uiAddress == null) {
            System.err.println("Warning: UI address not available");
            return;
        }
        
        // Check message size - UDP has practical limit of ~65507 bytes, but MTU is usually ~1500 bytes
        // For large messages, we'll skip sending to avoid errors
        byte[] messageBytes = encodedMessage.getBytes();
        int practicalMaxSize = 60000; // Use a safe limit below max (65507 is theoretical max)
        
        if (messageBytes.length > practicalMaxSize) {
            System.err.println("Warning: Message too large for UDP (" + messageBytes.length + " bytes). Skipping send to UI.");
            System.err.println("  Message type: StructuralInfosMsg (too many nodes)");
            return;
        }
        
        communication.sendMessage(uiAddress, encodedMessage);
    }
    
    // stop network
    public void endNetwork(EndMsg endMessage){
        isNetworkRunning = false;
        if (startRoundThread != null) {
            startRoundThread.interrupt();
        }
        if (system != null) {
            system.stopNetwork();
        }
    }

    public void startSystem(){ // run in main
        listenerThread = Thread.startVirtualThread(listener::listeningLoop);
        dispatcherThread = Thread.startVirtualThread(dispatcher::dispatchingLoop);
        workerThread = Thread.startVirtualThread(worker::generalFsmLogic);
    }
    
    /**
     * Initialize the supervisor socket to listen for incoming messages
     * @param supervisorPort Port number for supervisor to listen on
     */
    public void initialize(int supervisorPort) {
        this.supervisorPort = supervisorPort;
        Address supervisorAddress = new Address("127.0.0.1", supervisorPort);
        communication.setupSocket(supervisorAddress);
        System.out.println("Supervisor initialized and listening on " + supervisorAddress.getIp() + ":" + supervisorAddress.getPort());
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
