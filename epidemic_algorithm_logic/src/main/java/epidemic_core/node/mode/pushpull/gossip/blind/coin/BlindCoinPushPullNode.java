package epidemic_core.node.mode.pushpull.gossip.blind.coin;

import epidemic_core.message.common.MessageTopic;
import epidemic_core.node.mode.pushpull.gossip.GossipPushPullNode;
import general.communication.Communication;
import general.communication.utils.Address;

import java.util.List;
import java.util.Map;

// PushPullNode with Blind Coin Gossip protocol.
// After sending a pushpull request, tosses a coin with probability 1/k.
// If successful, the node stops making requests and spreading that message.
// If the message changes (new timestamp), it can propagate normally.
public class BlindCoinPushPullNode extends GossipPushPullNode {

    private final double k; // Probability parameter: 1/k chance to stop spreading

    // Constructor (uses default UdpCommunication)
    public BlindCoinPushPullNode(Integer id,
                                List<Integer> neighbours,
                                String assignedSubjectAsSource,
                                Map<Integer, Address> nodeIdToAddressTable,
                                List<MessageTopic> subscribedTopics,
                                Address supervisorAddress,
                                double k) {
        this(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, subscribedTopics, supervisorAddress, k, null);
    }

    // Constructor with optional Communication (used by DistributedNodeStub)
    public BlindCoinPushPullNode(Integer id,
                                List<Integer> neighbours,
                                String assignedSubjectAsSource,
                                Map<Integer, Address> nodeIdToAddressTable,
                                List<MessageTopic> subscribedTopics,
                                Address supervisorAddress,
                                double k,
                                Communication existingCommunication) {
        super(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, subscribedTopics, supervisorAddress, existingCommunication);
        this.k = k;
        
        // Set the worker to BlindCoinPushPullWorker
        this.worker = new BlindCoinPushPullWorker(this, replyMsgs, requestMsgs, startRoundMsgs, k);
    }

    public double getK() {
        return k;
    }

}

