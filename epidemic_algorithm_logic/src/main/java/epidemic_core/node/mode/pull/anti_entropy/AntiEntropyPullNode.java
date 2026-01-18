package epidemic_core.node.mode.pull.anti_entropy;

import epidemic_core.node.mode.pull.general.components.Dispatcher;
import epidemic_core.node.mode.pull.general.components.Listener;
import epidemic_core.node.mode.pull.general.components.WorkerInterface;
import epidemic_core.message.common.MessageTopic;
import general.communication.Communication;
import general.communication.utils.Address;
import epidemic_core.node.AntiEntropyNode;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class AntiEntropyPullNode extends AntiEntropyNode {

    public static final double RUNNING_INTERVAL = 50; // milliseconds

    // Pull Node Components
    protected Listener listener;
    protected Dispatcher dispatcher;
    protected WorkerInterface worker;

    // Msg buffers
    protected BlockingQueue<String> msgsQueue;
    protected BlockingQueue<String> replyMsgs;
    protected BlockingQueue<String> requestMsgs;
    protected BlockingQueue<String> startRoundMsgs;

    // Constructor (uses default UdpCommunication)
    public AntiEntropyPullNode(Integer id,
                    List<Integer> neighbours,
                    String assignedSubjectAsSource,
                    Map<Integer, Address> nodeIdToAddressTable,
                    List<MessageTopic> subscribedTopics,
                    Address supervisorAddress) {
        this(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, subscribedTopics, supervisorAddress, null);
    }

    // Constructor with optional Communication (used by DistributedNodeStub)
    public AntiEntropyPullNode(Integer id,
                    List<Integer> neighbours,
                    String assignedSubjectAsSource,
                    Map<Integer, Address> nodeIdToAddressTable,
                    List<MessageTopic> subscribedTopics,
                    Address supervisorAddress,
                    Communication existingCommunication) {

        super(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, subscribedTopics, supervisorAddress, existingCommunication);

        this.msgsQueue    = new LinkedBlockingQueue<>();
        this.replyMsgs    = new LinkedBlockingQueue<>();
        this.requestMsgs  = new LinkedBlockingQueue<>();
        this.startRoundMsgs = new LinkedBlockingQueue<>();

        this.listener     = new Listener(this, msgsQueue);
        this.dispatcher   = new Dispatcher(msgsQueue, replyMsgs, requestMsgs, startRoundMsgs);
        this.worker       = new AntiEntropyPullWorker(this, replyMsgs, requestMsgs, startRoundMsgs);
    }

    // ===========================================================
    //                        RUNNER
    // ===========================================================
    // Deprecated: Use StartRoundMsg instead
    @Deprecated
    public void triggerPullRound() {
        if (worker instanceof AntiEntropyPullWorker) {
            ((AntiEntropyPullWorker) worker).setStartSignal(true);
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

