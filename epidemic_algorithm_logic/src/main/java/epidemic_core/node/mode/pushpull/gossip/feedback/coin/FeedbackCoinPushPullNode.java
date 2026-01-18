package epidemic_core.node.mode.pushpull.gossip.feedback.coin;

import epidemic_core.message.common.MessageTopic;
import epidemic_core.node.mode.pushpull.gossip.GossipPushPullNode;
import general.communication.Communication;
import general.communication.utils.Address;

import java.util.List;
import java.util.Map;

// PushPullNode with Feedback Coin Gossip protocol.
// When receiving a request with MessageId that is already known, sends FeedbackMsg.
// When receiving FeedbackMsg, tosses a coin with probability 1/k.
// If successful, the node stops making requests and spreading that message.
// If the message changes (new timestamp), it can propagate normally.
public class FeedbackCoinPushPullNode extends GossipPushPullNode {

    private final double k; // Probability parameter: 1/k chance to stop spreading

    // Constructor (uses default UdpCommunication)
    public FeedbackCoinPushPullNode(Integer id,
                                    List<Integer> neighbours,
                                    String assignedSubjectAsSource,
                                    Map<Integer, Address> nodeIdToAddressTable,
                                    List<MessageTopic> subscribedTopics,
                                    Address supervisorAddress,
                                    double k) {
        this(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, subscribedTopics, supervisorAddress, k, null);
    }

    // Constructor with optional Communication (used by DistributedNodeStub)
    public FeedbackCoinPushPullNode(Integer id,
                                    List<Integer> neighbours,
                                    String assignedSubjectAsSource,
                                    Map<Integer, Address> nodeIdToAddressTable,
                                    List<MessageTopic> subscribedTopics,
                                    Address supervisorAddress,
                                    double k,
                                    Communication existingCommunication) {
        super(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, subscribedTopics, supervisorAddress, existingCommunication);
        this.k = k;
        
        // Set the worker to FeedbackCoinPushPullWorker
        this.worker = new FeedbackCoinPushPullWorker(this, replyMsgs, requestMsgs, startRoundMsgs, k);
    }

    public double getK() {
        return k;
    }

}



