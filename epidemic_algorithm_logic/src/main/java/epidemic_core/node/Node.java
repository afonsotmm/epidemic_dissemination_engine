package epidemic_core.node;

import epidemic_core.message.common.MessageId;
import epidemic_core.message.common.MessageTopic;
import epidemic_core.message.node_to_node.spread.SpreadMsg;
import epidemic_core.node.msg_related.NodeRole;
import epidemic_core.node.msg_related.NodeStatus;
import epidemic_core.node.msg_related.StatusForMessage;
import general.communication.Communication;
import general.communication.implementation.UdpCommunication;
import general.communication.utils.Address;
import epidemic_core.message.node_to_supervisor.infection_update.InfectionUpdateMsg;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

//  TODO: Gossip

public class Node {

    private Integer id;
    private List<Integer> neighbours;
    private String assignedSubjectAsSource; // e.g: "temperature"; "none" (if it's not source of any msg) etc
    private Map<Integer, Address> nodeIdToAddressTable; // also includes the supervisor address
    private Address supervisorAddress;
    private Communication communication;

    // Subject+SourceId that this node has interest
    private List<MessageTopic> subscribedTopics;

    // Stored by MessageId and includes the Status & Role of the node relative to that msg
    private Map<MessageId, StatusForMessage> storedMessages;

    // Constructor
    public Node(Integer id,
                List<Integer> neighbours,
                String assignedSubjectAsSource,
                Map<Integer, Address> nodeIdToAddressTable,
                List<MessageTopic> subscribedTopics,
                Address supervisorAddress) {

        this.id = id;
        this.neighbours = neighbours;
        this.assignedSubjectAsSource = assignedSubjectAsSource;
        this.nodeIdToAddressTable = nodeIdToAddressTable;
        this.supervisorAddress = supervisorAddress;
        this.subscribedTopics = subscribedTopics != null ? new ArrayList<>(subscribedTopics) : new ArrayList<>();
        this.storedMessages = new ConcurrentHashMap<>();
        this.communication = new UdpCommunication();

        // Initialize the socket to listen for incoming messages
        Address myAddress = nodeIdToAddressTable.get(id);
        if (myAddress != null) {
            this.communication.setupSocket(myAddress);
        } else {
            System.err.println("Warning: Node " + id + " address not found in nodeIdToAddressTable");
        }

        if(assignedSubjectAsSource != null) {
            generateAndStoreMessage();
        }
    }

    // General Methods
    // Check if node has a message with the given subject and sourceId
    public Boolean hasMessage(String subject, int sourceId) {
        return getMessagebySubjectAndSource(subject, sourceId) != null;
    }
    
    // checks if has any message with the subject (from any source)
    public Boolean hasMessage(String subject) {
        for (MessageId msgId : storedMessages.keySet()) {
            if (msgId.topic().subject().equals(subject)) {
                return true;
            }
        }
        return false;
    }

    // Get the most recent message by MessageTopic (subject + sourceId)
    public StatusForMessage getMessagebyTopic(MessageTopic topic) {
        StatusForMessage mostRecent = null;
        long maxTimestamp = -1;
        
        for (Map.Entry<MessageId, StatusForMessage> entry : storedMessages.entrySet()) {
            MessageId msgId = entry.getKey();
            if (msgId.topic().subject().equals(topic.subject()) 
                && msgId.topic().sourceId() == topic.sourceId() 
                && msgId.timestamp() > maxTimestamp) {
                mostRecent = entry.getValue();
                maxTimestamp = msgId.timestamp();
            }
        }
        
        return mostRecent;
    }
    
    // Get the most recent message by subject and sourceId
    public StatusForMessage getMessagebySubjectAndSource(String subject, int sourceId) {
        StatusForMessage mostRecent = null;
        long maxTimestamp = -1;
        
        for (Map.Entry<MessageId, StatusForMessage> entry : storedMessages.entrySet()) {
            MessageId msgId = entry.getKey();
            if (msgId.topic().subject().equals(subject) && msgId.topic().sourceId() == sourceId && msgId.timestamp() > maxTimestamp) {
                mostRecent = entry.getValue();
                maxTimestamp = msgId.timestamp();
            }
        }
        
        return mostRecent;
    }
    
    // get most recent message by subject (from any source)
    public StatusForMessage getMessagebySubject(String subject) {
        StatusForMessage mostRecent = null;
        long maxTimestamp = -1;
        
        for (Map.Entry<MessageId, StatusForMessage> entry : storedMessages.entrySet()) {
            MessageId msgId = entry.getKey();
            if (msgId.topic().subject().equals(subject) && msgId.timestamp() > maxTimestamp) {
                mostRecent = entry.getValue();
                maxTimestamp = msgId.timestamp();
            }
        }
        
        return mostRecent;
    }
    
    // Get message by exact MessageId
    public StatusForMessage getMessageById(MessageId messageId) {
        return storedMessages.get(messageId);
    }
    
    // Check if node has a message with the exact MessageId
    public Boolean hasMessageById(MessageId messageId) {
        return storedMessages.containsKey(messageId);
    }

    public List<Integer> getNeighbours() { return neighbours; }

    public Address getNeighbourAddress(Integer id) { return nodeIdToAddressTable.get(id); }

    public Communication getCommunication() { return communication; }

    public Integer getId() { return id; }

    // Sends a notification to supervisor about the Node's current status related to a given message
    public void notifyStatusSupervisor(NodeStatus statusToNotify, SpreadMsg message, int infectingNodeId) {
        // Create InfectionUpdateMsg
        MessageId msgId = message.getId();
        InfectionUpdateMsg infectionUpdateMsg = new InfectionUpdateMsg(
                msgId,
                id,  // updated_node_id (this node)
                infectingNodeId  // infecting_node_id (node that infected this one)
            );
        
        String encodedMessage = infectionUpdateMsg.encode();
        communication.sendMessage(supervisorAddress, encodedMessage);
    }
    
    // Overloaded method for backward compatibility (when node is SOURCE, it infects itself)
    public void notifyStatusSupervisor(NodeStatus statusToNotify, SpreadMsg message) {
        // If node is SOURCE, it infects itself (infecting_node_id = this node id)
        notifyStatusSupervisor(statusToNotify, message, id);
    }

    // Stores the message (overload for when infecting node is the originId of the message)
    public void storeMessage(SpreadMsg message, NodeRole role) {
        // Use originId as infecting node (the node that sent this message)
        storeMessage(message, role, message.getOriginId());
    }
    
    // Stores the message with infecting node ID
    public void storeMessage(SpreadMsg message, NodeRole role, int infectingNodeId) {

        MessageId msgId = message.getId();
        long receivedTimeStamp = msgId.timestamp();
        String receivedSubject = msgId.topic().subject();
        int sourceId = msgId.topic().sourceId();

        StatusForMessage newMessage = new StatusForMessage(message, role);
        
        // Remove any existing message with the same subject AND sourceId (keep only most recent from same source)
        storedMessages.entrySet().removeIf(entry -> {
            MessageId key = entry.getKey();
            return key.topic().subject().equals(receivedSubject) && key.topic().sourceId() == sourceId;
        });
        
        // Store the new message by MessageId
        storedMessages.put(msgId, newMessage);

        if(role == NodeRole.FORWARDER) {
        // Log: Node stored/updated message
        System.out.println("[Node " + id + "] Stored/Updated subject '" + receivedSubject +
                "' with value: " + message.getData() + " (timestamp: " + receivedTimeStamp + ", sourceId: " + sourceId + ")");
        } else if(role == NodeRole.SOURCE) {
            // Log: Node generated message as SOURCE
            System.out.println("[Node " + id + "] Generated as SOURCE - subject '" + assignedSubjectAsSource +
                    "' with value: " +  message.getData());
        }

        // If SOURCE, infecting node is itself; otherwise use the provided infectingNodeId
        int actualInfectingNodeId;
        if(role == NodeRole.SOURCE) {
            actualInfectingNodeId = id;
        }
        else {
            actualInfectingNodeId = infectingNodeId;
        }
        notifyStatusSupervisor(NodeStatus.INFECTED, message, actualInfectingNodeId);

    }

    // Stores the message only if it is new or more recent than the stored one
    // Assumes FORWARDER role by default (when receiving from another node)
    public boolean storeOrIgnoreMessage(SpreadMsg receivedMessage) {
        return storeOrIgnoreMessage(receivedMessage, NodeRole.FORWARDER);
    }

    public boolean storeOrIgnoreMessage(SpreadMsg receivedMessage, NodeRole role) {

        MessageId msgId = receivedMessage.getId();
        String subject = msgId.topic().subject();
        int sourceId = msgId.topic().sourceId();
        
        // Find existing message with the same subject AND sourceId (only compare timestamps from same source)
        StatusForMessage alrStoredMsg = getMessagebySubjectAndSource(subject, sourceId);

        if (alrStoredMsg == null || msgId.timestamp() > alrStoredMsg.getMessage().getId().timestamp()) {
            NodeRole roleToUse = role;
            
            // If role is SOURCE, always use SOURCE
            // If role is FORWARDER but node was already SOURCE, maintain SOURCE
            if (role == NodeRole.FORWARDER && alrStoredMsg != null && alrStoredMsg.getNodeRole() == NodeRole.SOURCE) {
                roleToUse = NodeRole.SOURCE;
            }
            // Otherwise, use the provided role (SOURCE or FORWARDER)
            // Pass originId as infecting node (the node that sent this message)
            int infectingNodeId = receivedMessage.getOriginId();
            storeMessage(receivedMessage, roleToUse, infectingNodeId);
            // Store
            return true;
        }
        // Ignore
        return false;
    }

    // Actuating as a SOURCE of a given msg:
    // Generates and stores a msg
    private void generateAndStoreMessage() {
        // Generate
        String data = randomDataGenerator(assignedSubjectAsSource);
        MessageTopic topic = new MessageTopic(assignedSubjectAsSource, id);
        MessageId messageId = new MessageId(topic, 0);
        SpreadMsg message = new SpreadMsg(messageId, id, data);

        //Store
        storeOrIgnoreMessage(message, NodeRole.SOURCE);
    }

    // Generates random data
    private String randomDataGenerator(String subject) {
        Random rand  = new Random();
        int num = rand.nextInt(100);
        return Integer.toString(num);
    }

    // Get all the stored messages
    public List<SpreadMsg> getAllStoredMessages() {
        List<SpreadMsg> messages = new ArrayList<>();

        for (Map.Entry<MessageId, StatusForMessage> entry : storedMessages.entrySet()) {
            SpreadMsg message = entry.getValue().getMessage();
            messages.add(message);
        }

        return messages;
    }
    
    // Get all stored messages with their status (for GUI)
    public Map<MessageId, StatusForMessage> getAllStoredMessagesWithStatus() {
        return new ConcurrentHashMap<>(storedMessages);
    }
    
    // Check if node is a source
    public boolean isSource() {
        return assignedSubjectAsSource != null;
    }
    
    // Get assigned subject as source
    public String getAssignedSubjectAsSource() {
        return assignedSubjectAsSource;
    }

    // Get subscribed topics (interests)
    public List<MessageTopic> getSubscribedTopics() { return subscribedTopics; }

    public boolean subscriptionCheck(MessageTopic topic) {
        for(MessageTopic subsTopic : subscribedTopics) {
            // Check if both subject AND sourceId match
            if(topic.subject().equals(subsTopic.subject()) && topic.sourceId() == subsTopic.sourceId()) {
                return true;
            }
        }
        return false;
    }

    // Print current state of all subjects stored in this node
    public void printNodeState() {
        if (!storedMessages.isEmpty()) {
            System.out.println("[Node " + id + "] Current subjects:");
            for (Map.Entry<MessageId, StatusForMessage> entry : storedMessages.entrySet()) {
                MessageId msgId = entry.getKey();
                SpreadMsg message = entry.getValue().getMessage();
                NodeRole role = entry.getValue().getNodeRole();
                System.out.println("  - Subject: '" + msgId.topic().subject() + "' | Value: " + message.getData() + 
                        " | Timestamp: " + msgId.timestamp() + " | SourceId: " + msgId.topic().sourceId() + " | Role: " + role);
            }
        }
    }

}

