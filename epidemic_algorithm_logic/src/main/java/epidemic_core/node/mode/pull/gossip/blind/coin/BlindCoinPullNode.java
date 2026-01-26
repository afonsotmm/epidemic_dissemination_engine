package epidemic_core.node.mode.pull.gossip.blind.coin;

import epidemic_core.message.common.MessageTopic;
import epidemic_core.node.mode.pull.gossip.GossipPullNode;
import general.communication.Communication;
import general.communication.utils.Address;

import java.util.List;
import java.util.Map;

public class BlindCoinPullNode extends GossipPullNode {

    private final double k;

    public BlindCoinPullNode(Integer id,
                            List<Integer> neighbours,
                            String assignedSubjectAsSource,
                            Map<Integer, Address> nodeIdToAddressTable,
                            List<MessageTopic> subscribedTopics,
                            Address supervisorAddress,
                            double k) {
        this(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, subscribedTopics, supervisorAddress, k, null);
    }

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

        this.worker = new BlindCoinPullWorker(this, replyMsgs, requestMsgs, startRoundMsgs, k);
    }

    public double getK() {
        return k;
    }

}

