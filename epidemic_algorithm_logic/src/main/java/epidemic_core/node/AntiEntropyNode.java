package epidemic_core.node;

import epidemic_core.message.common.MessageTopic;
import general.communication.Communication;
import general.communication.utils.Address;

import java.util.List;
import java.util.Map;


// Abstract base class for Anti-Entropy protocol nodes. Basically the same as the base Node abstract class

public abstract class AntiEntropyNode extends Node {

    // Constructor (uses default UdpCommunication)
    public AntiEntropyNode(Integer id,
                           List<Integer> neighbours,
                           String assignedSubjectAsSource,
                           Map<Integer, Address> nodeIdToAddressTable,
                           List<MessageTopic> subscribedTopics,
                           Address supervisorAddress) {
        super(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, subscribedTopics, supervisorAddress);
    }

    // Constructor with optional Communication (used by DistributedNodeStub)
    public AntiEntropyNode(Integer id,
                           List<Integer> neighbours,
                           String assignedSubjectAsSource,
                           Map<Integer, Address> nodeIdToAddressTable,
                           List<MessageTopic> subscribedTopics,
                           Address supervisorAddress,
                           Communication existingCommunication) {
        super(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, subscribedTopics, supervisorAddress, existingCommunication);
    }

}

