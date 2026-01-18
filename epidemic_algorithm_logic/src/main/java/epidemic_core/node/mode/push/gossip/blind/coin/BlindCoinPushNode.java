package epidemic_core.node.mode.push.gossip.blind.coin;

import epidemic_core.message.common.MessageTopic;
import epidemic_core.node.mode.push.gossip.GossipPushNode;
import general.communication.Communication;
import general.communication.utils.Address;

import java.util.List;
import java.util.Map;

/**
 * PushNode with Blind Coin Gossip protocol.
 * After pushing a message, tosses a coin with probability 1/k.
 * If successful, the node stops spreading that message.
 */
public class BlindCoinPushNode extends GossipPushNode {

    private final double k; // Probability parameter: 1/k chance to stop spreading

    // Constructor (uses default UdpCommunication)
    public BlindCoinPushNode(Integer id,
                            List<Integer> neighbours,
                            String assignedSubjectAsSource,
                            Map<Integer, Address> nodeIdToAddressTable,
                            List<MessageTopic> subscribedTopics,
                            Address supervisorAddress,
                            double k) {
        this(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, subscribedTopics, supervisorAddress, k, null);
    }

    // Constructor with optional Communication (used by DistributedNodeStub)
    public BlindCoinPushNode(Integer id,
                            List<Integer> neighbours,
                            String assignedSubjectAsSource,
                            Map<Integer, Address> nodeIdToAddressTable,
                            List<MessageTopic> subscribedTopics,
                            Address supervisorAddress,
                            double k,
                            Communication existingCommunication) {
        super(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, subscribedTopics, supervisorAddress, existingCommunication);
        this.k = k;
        
        // Set the worker to BlindCoinPushWorker
        this.worker = new BlindCoinPushWorker(this, pushMsgs, startRoundMsgs, k);
    }

    public double getK() {
        return k;
    }

}

