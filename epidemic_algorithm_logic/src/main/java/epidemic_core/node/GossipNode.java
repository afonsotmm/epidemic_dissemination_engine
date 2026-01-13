package epidemic_core.node;

import epidemic_core.message.common.MessageId;
import epidemic_core.message.common.MessageTopic;
import epidemic_core.message.node_to_supervisor.remotion_update.RemotionUpdateMsg;
import general.communication.utils.Address;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


// Abstract base class for Gossip protocol nodes.
// Gossip nodes have message removal functionality and toss.

public abstract class GossipNode extends Node {

    // Messages that have been "removed" (for Gossip protocols - cannot spread/request anymore)
    // Using MessageId (topic + timestamp) so nodes can still spread newer versions of the same topic
    protected Set<MessageId> removedMessages;
    private static final Random rand = new Random();

    // Constructor
    public GossipNode(Integer id,
                     List<Integer> neighbours,
                     String assignedSubjectAsSource,
                     Map<Integer, Address> nodeIdToAddressTable,
                     List<MessageTopic> subscribedTopics,
                     Address supervisorAddress) {
        super(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, subscribedTopics, supervisorAddress);
        this.removedMessages = ConcurrentHashMap.newKeySet(); // Thread-safe Set
    }

    // ======================================================= //
    //                  GOSSIP METHODS                         //
    // ======================================================= //
    
    // Check if a message (by MessageId) has been removed
    public boolean isMessageRemoved(MessageId messageId) {
        return removedMessages.contains(messageId);
    }
    
    // Remove a message (by MessageId) and notify supervisor
    public void removeMessage(MessageId messageId) {
        removedMessages.add(messageId);
        // Notify supervisor about the removal
        notifyRemotionSupervisor(messageId);
    }
    
    // Notify supervisor that this node has removed a message (for Gossip protocols)
    private void notifyRemotionSupervisor(MessageId messageId) {
        RemotionUpdateMsg remotionUpdateMsg = new RemotionUpdateMsg(
                messageId,
                id  // updated_node_id (this node)
        );
        
        String encodedMessage = remotionUpdateMsg.encode();
        communication.sendMessage(supervisorAddress, encodedMessage);
    }

    // Coin variant: Toss a coin with 1/k probability of returning true
    // k can be a double to allow more precise probabilities (e.g., k=2.5 means 40% chance)
    public static boolean tossCoin(double k) { 
        return rand.nextDouble() < (1.0 / k); 
    }

}

