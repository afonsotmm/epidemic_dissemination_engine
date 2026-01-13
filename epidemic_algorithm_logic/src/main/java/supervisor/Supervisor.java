package supervisor;

import epidemic_core.message.ui_to_supervisor.end_system.EndMsg;
import epidemic_core.message.ui_to_supervisor.start_system.StartMsg;
import general.communication.Communication;
import general.communication.implementation.UdpCommunication;
import supervisor.communication.Dispatcher;
import supervisor.communication.Listener;
import supervisor.communication.Worker;
import supervisor.network_emulation.NetworkEmulator;

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

    private StartMsg startMessage;

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

    // initialize topology and nodes
    public void startNetwork(StartMsg startMessage){
        this.startMessage = startMessage;

        system = new NetworkEmulator(startMessage.getN(), startMessage.getSourceNodes(), startMessage.getTopology(), startMessage.getProtocol(), startMessage.getMode());
        system.initializeNetwork();
    }
    
    // stop network
    public void endNetwork(EndMsg endMessage){
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
