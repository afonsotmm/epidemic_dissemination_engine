package epidemic_core.node.mode.push;

import epidemic_core.node.mode.push.components.Dispatcher;
import epidemic_core.node.mode.push.components.Listener;
import epidemic_core.node.mode.push.components.Worker;
import general.communication.utils.Address;
import epidemic_core.node.Node;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

// =================================================================
//                     Guide to use this class
// =================================================================
//  -> To start a round: triggerPushRound()
//  ...
// =================================================================

public class PushNode extends Node {

    public static final double RUNNING_INTERVAL = 50; // milliseconds

    // Push Node Components
    Listener listener;
    Dispatcher dispatcher;
    Worker worker;

    // Msg buffers
    private BlockingQueue<String> receivedMsgsQueue;
    private BlockingQueue<String> pushMsgs;

    // Constructor
    public PushNode(Integer id,
                    List<Integer> neighbours,
                    String assignedSubjectAsSource,
                    Map<Integer, Address> nodeIdToAddressTable,
                    Address supervisorAddress) {

        super(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, supervisorAddress);

        this.receivedMsgsQueue    = new LinkedBlockingQueue<>();
        this.pushMsgs             = new LinkedBlockingQueue<>();

        this.listener   = new Listener(this, receivedMsgsQueue);
        this.dispatcher = new Dispatcher(receivedMsgsQueue, pushMsgs);
        this.worker     = new Worker(this, pushMsgs);
    }

    // ===========================================================
    //                        RUNNER
    // ===========================================================
    public void triggerPushRound() {
        worker.setStartSignal(true);
    }

    public void startRunning() {
        Thread.startVirtualThread(listener::listeningLoop);
        Thread.startVirtualThread(dispatcher::dispatchingLoop);
        Thread.startVirtualThread(worker::workingLoop);
    }

}

