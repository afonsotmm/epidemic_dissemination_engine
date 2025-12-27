package epidemic_core.node.mode.pushpull;

import epidemic_core.node.mode.pushpull.components.Dispatcher;
import epidemic_core.node.mode.pushpull.components.Listener;
import epidemic_core.node.mode.pushpull.components.Worker;
import epidemic_core.message.common.MessageTopic;
import general.communication.utils.Address;
import epidemic_core.node.Node;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

// =================================================================
//                     Guide to use this class
// =================================================================
//  -> To start a round: triggerPushPullRound()
//  ...
// =================================================================

public class PushPullNode extends Node {

    public static final double RUNNING_INTERVAL = 50; // milliseconds

    // PushPull Node Components
    private Listener listener;
    private Dispatcher dispatcher;
    private Worker worker;

    // Msg buffers
    private BlockingQueue<String> msgsQueue;
    private BlockingQueue<String> replyMsgs;
    private BlockingQueue<String> requestMsgs;
    private BlockingQueue<String> startRoundMsgs;

    // Constructor
    public PushPullNode(Integer id,
                    List<Integer> neighbours,
                    String assignedSubjectAsSource,
                    Map<Integer, Address> nodeIdToAddressTable,
                    List<MessageTopic> subscribedTopics,
                    Address supervisorAddress) {

        super(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, subscribedTopics, supervisorAddress);

        this.msgsQueue    = new LinkedBlockingQueue<>();
        this.replyMsgs    = new LinkedBlockingQueue<>();
        this.requestMsgs  = new LinkedBlockingQueue<>();
        this.startRoundMsgs = new LinkedBlockingQueue<>();

        this.listener     = new Listener(this, msgsQueue);
        this.dispatcher   = new Dispatcher(msgsQueue, replyMsgs, requestMsgs, startRoundMsgs);
        this.worker       = new Worker(this, replyMsgs, requestMsgs, startRoundMsgs);
    }

    // ===========================================================
    //                        RUNNER
    // ===========================================================
    // Deprecated: Use StartRoundMsg instead
    @Deprecated
    public void triggerPushPullRound() {
        worker.setStartSignal(true);
    }

    public void startRunning() {
        Thread.startVirtualThread(listener::listeningLoop);
        Thread.startVirtualThread(dispatcher::dispatchingLoop);
        Thread.startVirtualThread(worker::workingLoop);
    }

}