package epidemic_core.node.mode.pushpull.gossip;

import epidemic_core.node.mode.pushpull.general.components.Dispatcher;
import epidemic_core.node.mode.pushpull.general.components.Listener;
import epidemic_core.node.mode.pushpull.general.components.WorkerInterface;
import epidemic_core.node.GossipNode;
import epidemic_core.message.common.MessageTopic;
import general.communication.utils.Address;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

// Abstract base class for Gossip PushPull protocol nodes.
// Provides common structure for BlindCoinPushPullNode, FeedbackCoinPushPullNode.

public abstract class GossipPushPullNode extends GossipNode {

    public static final double RUNNING_INTERVAL = 50; // milliseconds

    // PushPull Node Components
    protected Listener listener;
    protected Dispatcher dispatcher;
    protected WorkerInterface worker;

    // Msg buffers
    protected BlockingQueue<String> receivedMsgsQueue;
    protected BlockingQueue<String> replyMsgs;
    protected BlockingQueue<String> requestMsgs;
    protected BlockingQueue<String> startRoundMsgs;

    // Constructor
    public GossipPushPullNode(Integer id,
                             List<Integer> neighbours,
                             String assignedSubjectAsSource,
                             Map<Integer, Address> nodeIdToAddressTable,
                             List<MessageTopic> subscribedTopics,
                             Address supervisorAddress) {

        super(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, subscribedTopics, supervisorAddress);

        this.receivedMsgsQueue    = new LinkedBlockingQueue<>();
        this.replyMsgs            = new LinkedBlockingQueue<>();
        this.requestMsgs          = new LinkedBlockingQueue<>();
        this.startRoundMsgs       = new LinkedBlockingQueue<>();

        this.listener   = new Listener(this, receivedMsgsQueue);
        this.dispatcher = new Dispatcher(receivedMsgsQueue, replyMsgs, requestMsgs, startRoundMsgs);
    }

    // Worker must be set by subclasses (e.g., BlindCoinPushPullWorker, FeedbackCoinPushPullWorker)

    // ===========================================================
    //                        RUNNER
    // ===========================================================
    public void startRunning() {
        Thread.startVirtualThread(listener::listeningLoop);
        Thread.startVirtualThread(dispatcher::dispatchingLoop);
        Thread.startVirtualThread(worker::workingLoop);
    }
    
    public void stopRunning() {
        stop(); // Set isRunning flag to false
        listener.stopListening();
        dispatcher.stopDispatching();
    }

}

