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

import java.util.Map;
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
        
        system = new NetworkEmulator(startMessage.getN(), startMessage.getSourceNodes(), startMessage.getTopology(), startMessage.getProtocol(), startMessage.getMode());
        system.initializeNetwork();
        
        // Send structural information to UI
        sendStructuralInfosToUi();
        
        // Start sending start_round messages
        isNetworkRunning = true;
        startRoundThread = Thread.startVirtualThread(this::sendStartRoundPeriodically);
    }
    
    // Send structural information to UI
    private void sendStructuralInfosToUi() {
        try {
            supervisor.network_emulation.neighbors_and_subject.StructuralInfosMatrix matrix = system.getStructuralInfosMatrix();
            StructuralInfosMsg msg = StructuralInfosMsg.fromStructuralInfosMatrix(matrix);
            sendToUi(msg.encode());
        } catch (Exception e) {
            System.err.println("Error sending structural infos to UI: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendStartRoundPeriodically() {
        try {
            while (isNetworkRunning && system != null) {
                StartRoundMsg startRoundMsg = new StartRoundMsg(
                    epidemic_core.message.common.Direction.supervisor_to_node.toString(),
                    epidemic_core.message.supervisor_to_node.SupervisorToNodeMessageType.start_round.toString()
                );
                String encodedMsg = startRoundMsg.encode();
                
                Map<Integer, Address> nodeAddresses = system.getNodeAddresses();
                for (Map.Entry<Integer, Address> entry : nodeAddresses.entrySet()) {
                    Address nodeAddress = entry.getValue();
                    if (nodeAddress != null) {
                        communication.sendMessage(nodeAddress, encodedMsg);
                    }
                }
                
                Thread.sleep(roundInterval * 1000); // Sleep for 2 seconds
            }
        } catch (Exception e) {
            System.err.println("Error in start round message: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Send message to UI
    public void sendToUi(String encodedMessage) {
        Address uiAddress = getUiAddress();
        if (uiAddress == null) {
            System.err.println("Warning: UI address not available");
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
}
