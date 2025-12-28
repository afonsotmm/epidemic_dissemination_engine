package epidemic_core.node.mode.push.gossip.feedback.coin;

import epidemic_core.message.common.MessageTopic;
import epidemic_core.node.mode.push.gossip.GossipPushNode;
import general.communication.utils.Address;

import java.util.List;
import java.util.Map;

// PushNode with Feedback Coin Gossip protocol.
// When receiving a spread message that is already known, sends FeedbackMsg.
// When receiving FeedbackMsg, tosses a coin with probability 1/k.
// If successful, the node stops spreading that message.
public class FeedbackCoinPushNode extends GossipPushNode {

    private final double k; // Probability parameter: 1/k chance to stop spreading

    // Constructor
    public FeedbackCoinPushNode(Integer id,
                                List<Integer> neighbours,
                                String assignedSubjectAsSource,
                                Map<Integer, Address> nodeIdToAddressTable,
                                List<MessageTopic> subscribedTopics,
                                Address supervisorAddress,
                                double k) {
        super(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, subscribedTopics, supervisorAddress);
        this.k = k;
        
        // Set the worker to FeedbackCoinPushWorker
        this.worker = new FeedbackCoinPushWorker(this, pushMsgs, startRoundMsgs, k);
    }

    public double getK() {
        return k;
    }

}

