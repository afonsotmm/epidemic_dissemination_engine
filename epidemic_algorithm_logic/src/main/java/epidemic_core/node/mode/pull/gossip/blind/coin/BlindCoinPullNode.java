package epidemic_core.node.mode.pull.gossip.blind.coin;

import epidemic_core.message.common.MessageTopic;
import epidemic_core.node.mode.pull.gossip.GossipPullNode;
import general.communication.Communication;
import general.communication.utils.Address;

import java.util.List;
import java.util.Map;

// PullNode with Blind Coin Gossip protocol.
// After sending a pull request, tosses a coin with probability 1/k.
// If successful, the node stops making requests and spreading that message.
public class BlindCoinPullNode extends GossipPullNode {

    private final double k; // Probability parameter: 1/k chance to stop spreading

    // Constructor (uses default UdpCommunication)
    public BlindCoinPullNode(Integer id,
                            List<Integer> neighbours,
                            String assignedSubjectAsSource,
                            Map<Integer, Address> nodeIdToAddressTable,
                            List<MessageTopic> subscribedTopics,
                            Address supervisorAddress,
                            double k) {
        this(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, subscribedTopics, supervisorAddress, k, null);
    }

    // Constructor with optional Communication (used by DistributedNodeStub)
    public BlindCoinPullNode(Integer id,
                            List<Integer> neighbours,
                            String assignedSubjectAsSource,
                            Map<Integer, Address> nodeIdToAddressTable,
                            List<MessageTopic> subscribedTopics,
                            Address supervisorAddress,
                            double k,
                            Communication existingCommunication) {
        super(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, subscribedTopics, supervisorAddress, existingCommunication);
        this.k = k;
        
        // Set the worker to BlindCoinPullWorker
        this.worker = new BlindCoinPullWorker(this, replyMsgs, requestMsgs, startRoundMsgs, k);
    }

    public double getK() {
        return k;
    }

}

