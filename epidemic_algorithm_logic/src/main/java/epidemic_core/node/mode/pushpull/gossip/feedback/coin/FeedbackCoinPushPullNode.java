package epidemic_core.node.mode.pushpull.gossip.feedback.coin;

import epidemic_core.message.common.MessageTopic;
import epidemic_core.node.GossipNode;
import epidemic_core.node.mode.pushpull.components.Dispatcher;
import epidemic_core.node.mode.pushpull.components.Listener;
import general.communication.utils.Address;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

// PushPullNode with Feedback Coin Gossip protocol.
// When receiving a request with MessageId that is already known, sends FeedbackMsg.
// When receiving FeedbackMsg, tosses a coin with probability 1/k.
// If successful, the node stops making requests and spreading that message.
// If the message changes (new timestamp), it can propagate normally.
public class FeedbackCoinPushPullNode extends GossipNode {

    public static final double RUNNING_INTERVAL = 50; // milliseconds

    // PushPull Node Components
    protected Listener listener;
    protected Dispatcher dispatcher;
    protected FeedbackCoinPushPullWorker worker;

    // Msg buffers
    protected BlockingQueue<String> msgsQueue;
    protected BlockingQueue<String> replyMsgs;
    protected BlockingQueue<String> requestMsgs;
    protected BlockingQueue<String> startRoundMsgs;

    private final double k; // Probability parameter: 1/k chance to stop spreading

    // Constructor
    public FeedbackCoinPushPullNode(Integer id,
                                    List<Integer> neighbours,
                                    String assignedSubjectAsSource,
                                    Map<Integer, Address> nodeIdToAddressTable,
                                    List<MessageTopic> subscribedTopics,
                                    Address supervisorAddress,
                                    double k) {
        super(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, subscribedTopics, supervisorAddress);
        this.k = k;

        this.msgsQueue    = new LinkedBlockingQueue<>();
        this.replyMsgs    = new LinkedBlockingQueue<>();
        this.requestMsgs  = new LinkedBlockingQueue<>();
        this.startRoundMsgs = new LinkedBlockingQueue<>();

        this.listener     = new Listener(this, msgsQueue);
        this.dispatcher   = new Dispatcher(msgsQueue, replyMsgs, requestMsgs, startRoundMsgs);
        this.worker       = new FeedbackCoinPushPullWorker(this, replyMsgs, requestMsgs, startRoundMsgs, k);
    }

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

    public double getK() {
        return k;
    }

}

