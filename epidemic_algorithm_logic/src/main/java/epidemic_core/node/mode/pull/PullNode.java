package epidemic_core.node.mode.pull;

import epidemic_core.node.mode.pull.components.Dispatcher;
import epidemic_core.node.mode.pull.components.Listener;
import epidemic_core.node.mode.pull.components.Worker;
import general.communication.utils.Address;
import epidemic_core.node.Node;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

// =================================================================
//                     Guide to use this class
// =================================================================
//  -> To start a round: triggerPullRound()
//  ...
// =================================================================

public class PullNode extends Node {

    public static final double RUNNING_INTERVAL = 50; // milliseconds

    // Pull Node Components
    private Listener listener;
    private Dispatcher dispatcher;
    private Worker worker;

    // Msg buffers
    private BlockingQueue<String> msgsQueue;
    private BlockingQueue<String> replyMsgs;
    private BlockingQueue<String> requestMsgs;

    // Constructor
    public PullNode(Integer id,
                    List<Integer> neighbours,
                    String assignedSubjectAsSource,
                    Map<Integer, Address> nodeIdToAddressTable,
                    Address supervisorAddress) {

        super(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, supervisorAddress);

        this.msgsQueue    = new LinkedBlockingQueue<>();
        this.replyMsgs    = new LinkedBlockingQueue<>();
        this.requestMsgs  = new LinkedBlockingQueue<>();

        this.listener     = new Listener(this, msgsQueue);
        this.dispatcher   = new Dispatcher(msgsQueue, replyMsgs, requestMsgs);
        this.worker       = new Worker(this, replyMsgs, requestMsgs);
    }

    // ===========================================================
    //                        RUNNER
    // ===========================================================
    public void triggerPullRound() {
        worker.setStartSignal(true);
    }

    public void startRunning() {
        Thread.startVirtualThread(listener::listeningLoop);
        Thread.startVirtualThread(dispatcher::dispatchingLoop);
        Thread.startVirtualThread(worker::workingLoop);
    }

}

