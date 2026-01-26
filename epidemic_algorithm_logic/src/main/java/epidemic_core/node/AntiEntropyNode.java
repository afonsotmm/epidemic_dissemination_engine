package epidemic_core.node;

import epidemic_core.message.common.MessageTopic;
import general.communication.Communication;
import general.communication.utils.Address;

import java.util.List;
import java.util.Map;

public abstract class AntiEntropyNode extends Node {

    public AntiEntropyNode(Integer id,
                           List<Integer> neighbours,
                           String assignedSubjectAsSource,
                           Map<Integer, Address> nodeIdToAddressTable,
                           List<MessageTopic> subscribedTopics,
                           Address supervisorAddress) {
        super(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, subscribedTopics, supervisorAddress);
    }

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

