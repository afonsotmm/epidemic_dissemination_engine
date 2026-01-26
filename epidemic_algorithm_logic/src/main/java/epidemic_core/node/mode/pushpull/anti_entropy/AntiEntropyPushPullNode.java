package epidemic_core.node.mode.pushpull.anti_entropy;

import epidemic_core.node.mode.pushpull.general.components.Dispatcher;
import epidemic_core.node.mode.pushpull.general.components.Listener;
import epidemic_core.node.mode.pushpull.general.components.WorkerInterface;
import epidemic_core.message.common.MessageTopic;
import general.communication.Communication;
import general.communication.utils.Address;
import epidemic_core.node.AntiEntropyNode;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class AntiEntropyPushPullNode extends AntiEntropyNode {

    public static final double RUNNING_INTERVAL = 50; // milliseconds

    protected Listener listener;
    protected Dispatcher dispatcher;
    protected WorkerInterface worker;

    protected BlockingQueue<String> receivedMsgsQueue;
    protected BlockingQueue<String> replyMsgs;
    protected BlockingQueue<String> requestMsgs;
    protected BlockingQueue<String> startRoundMsgs;

    public AntiEntropyPushPullNode(Integer id,
                    List<Integer> neighbours,
                    String assignedSubjectAsSource,
                    Map<Integer, Address> nodeIdToAddressTable,
                    List<MessageTopic> subscribedTopics,
                    Address supervisorAddress) {
        this(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, subscribedTopics, supervisorAddress, null);
    }

    public AntiEntropyPushPullNode(Integer id,
                    List<Integer> neighbours,
                    String assignedSubjectAsSource,
                    Map<Integer, Address> nodeIdToAddressTable,
                    List<MessageTopic> subscribedTopics,
                    Address supervisorAddress,
                    Communication existingCommunication) {

        super(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, subscribedTopics, supervisorAddress, existingCommunication);

        this.receivedMsgsQueue    = new LinkedBlockingQueue<>();
        this.replyMsgs           = new LinkedBlockingQueue<>();
        this.requestMsgs         = new LinkedBlockingQueue<>();
        this.startRoundMsgs      = new LinkedBlockingQueue<>();

        this.listener   = new Listener(this, receivedMsgsQueue);
        this.dispatcher = new Dispatcher(receivedMsgsQueue, replyMsgs, requestMsgs, startRoundMsgs);
        this.worker     = new AntiEntropyPushPullWorker(this, replyMsgs, requestMsgs, startRoundMsgs);
    }

    // ===========================================================
    //                        RUNNER
    // ===========================================================

    @Deprecated
    public void triggerPushPullRound() {
        if (worker instanceof AntiEntropyPushPullWorker) {
            ((AntiEntropyPushPullWorker) worker).setStartSignal(true);
        }
    }

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

