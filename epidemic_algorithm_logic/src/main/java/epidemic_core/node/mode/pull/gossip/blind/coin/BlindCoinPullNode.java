package epidemic_core.node.mode.pull.gossip.blind.coin;

import epidemic_core.message.common.MessageTopic;
import epidemic_core.node.mode.pull.gossip.GossipPullNode;
import general.communication.utils.Address;

import java.util.List;
import java.util.Map;

// PullNode with Blind Coin Gossip protocol.
// After sending a pull request, tosses a coin with probability 1/k.
// If successful, the node stops making requests and spreading that message.
public class BlindCoinPullNode extends GossipPullNode {

    private final double k; // Probability parameter: 1/k chance to stop spreading

    // Constructor
    public BlindCoinPullNode(Integer id,
                            List<Integer> neighbours,
                            String assignedSubjectAsSource,
                            Map<Integer, Address> nodeIdToAddressTable,
                            List<MessageTopic> subscribedTopics,
                            Address supervisorAddress,
                            double k) {
        super(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, subscribedTopics, supervisorAddress);
        this.k = k;
        
        // Set the worker to BlindCoinPullWorker
        this.worker = new BlindCoinPullWorker(this, replyMsgs, requestMsgs, startRoundMsgs, k);
    }

    public double getK() {
        return k;
    }

}

