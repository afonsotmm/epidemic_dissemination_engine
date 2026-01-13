package epidemic_core.node;

import epidemic_core.message.common.MessageTopic;
import general.communication.utils.Address;

import java.util.List;
import java.util.Map;


// Abstract base class for Anti-Entropy protocol nodes. Basically the same as the base Node abstract class

public abstract class AntiEntropyNode extends Node {

    // Constructor
    public AntiEntropyNode(Integer id,
                           List<Integer> neighbours,
                           String assignedSubjectAsSource,
                           Map<Integer, Address> nodeIdToAddressTable,
                           List<MessageTopic> subscribedTopics,
                           Address supervisorAddress) {
        super(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, subscribedTopics, supervisorAddress);
    }

}

