package supervisor;

import epidemic_core.message.FromUI.StartMessage;
import general.communication.Communication;
import general.communication.implementation.UdpCommunication;
import supervisor.communication.Dispatcher;
import supervisor.communication.Listener;
import supervisor.communication.Worker;
import supervisor.network_emulation.NetworkEmulator;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Supervisor{

    private Communication communication;

    private Listener listener;
    private Dispatcher dispatcher;
    private Worker worker;
    private NetworkEmulator system;

    private BlockingQueue<String> msgsQueue;
    private BlockingQueue<String> nodeQueue;
    private BlockingQueue<String> uiQueue;

    private StartMessage startMessage;

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
    public void startNetwork(StartMessage startMessage){ // run in worker when it receives a START msg
        this.startMessage = startMessage;

        system = new NetworkEmulator(startMessage.getN(), startMessage.getSourceNodes(), startMessage.getTopology(), startMessage.getMode());
        system.initializeNetwork();
    }

    public void startSystem(){ // run in main
        Thread.startVirtualThread(listener::listeningLoop);
        Thread.startVirtualThread(dispatcher::dispatchingLoop);
        Thread.startVirtualThread(worker::generalFsmLogic);
    }
}
