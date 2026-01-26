package epidemic_core.node;

import epidemic_core.message.common.Direction;
import epidemic_core.message.common.MessageId;
import epidemic_core.message.common.MessageTopic;
import epidemic_core.message.node_to_node.NodeToNodeMessageType;
import epidemic_core.message.node_to_node.spread.SpreadMsg;
import epidemic_core.message.node_to_supervisor.NodeToSupervisorMessageType;
import epidemic_core.message.node_to_supervisor.infection_update.InfectionUpdateMsg;
import epidemic_core.node.msg_related.NodeRole;
import epidemic_core.node.msg_related.NodeStatus;
import epidemic_core.node.msg_related.StatusForMessage;
import general.communication.Communication;
import general.communication.implementation.NodeToNodeCountingCommunication;
import general.communication.implementation.UdpCommunication;
import general.communication.utils.Address;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Node {

    protected Integer id;
    protected List<Integer> neighbours;
    protected String assignedSubjectAsSource;
    protected Map<Integer, Address> nodeIdToAddressTable;
    protected Address supervisorAddress;
    protected Communication communication; // UDP for node-to-node communication
    protected Communication supervisorTcpCommunication; // TCP for supervisor communication (distributed mode)

    // Subject+SourceId that this node has interest
    protected List<MessageTopic> subscribedTopics;

    // Stored by MessageId and includes the Status & Role of the node relative to that msg
    protected Map<MessageId, StatusForMessage> storedMessages;

    protected volatile boolean isRunning;

    // Constructor
    public Node(Integer id,
                List<Integer> neighbours,
                String assignedSubjectAsSource,
                Map<Integer, Address> nodeIdToAddressTable,
                List<MessageTopic> subscribedTopics,
                Address supervisorAddress) {
        this(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, subscribedTopics, supervisorAddress, null);
    }

    // Constructor with optional Communication (used by DistributedNodeStub to reuse existing socket)
    public Node(Integer id,
                List<Integer> neighbours,
                String assignedSubjectAsSource,
                Map<Integer, Address> nodeIdToAddressTable,
                List<MessageTopic> subscribedTopics,
                Address supervisorAddress,
                Communication existingCommunication) {

        this.id = id;
        this.neighbours = neighbours;
        this.assignedSubjectAsSource = assignedSubjectAsSource;
        this.nodeIdToAddressTable = nodeIdToAddressTable;
        this.supervisorAddress = supervisorAddress;
        this.subscribedTopics = subscribedTopics != null ? new ArrayList<>(subscribedTopics) : new ArrayList<>();
        this.storedMessages = new ConcurrentHashMap<>();
        this.isRunning = true;

        Communication raw = existingCommunication != null
                ? existingCommunication
                : new UdpCommunication();
        this.communication = new NodeToNodeCountingCommunication(raw);
        if (existingCommunication == null) {
            Address myAddress = nodeIdToAddressTable.get(id);
            if (myAddress != null) {
                this.communication.setupSocket(myAddress);
            } else {
                System.err.println("Warning: Node " + id + " address not found in nodeIdToAddressTable");
            }
        }

        if(assignedSubjectAsSource != null) {
            generateAndStoreMessage();
        }
    }

    public Boolean hasMessage(String subject, int sourceId) {
        return getMessagebySubjectAndSource(subject, sourceId) != null;
    }

    public Boolean hasMessage(String subject) {
        for (MessageId msgId : storedMessages.keySet()) {
            if (msgId.topic().subject().equals(subject)) {
                return true;
            }
        }
        return false;
    }

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
                Direction.node_to_supervisor.toString(),
                NodeToSupervisorMessageType.infection_update.toString(),
                id,  // updated_node_id (this node)
                infectingNodeId,  // infecting_node_id (node that infected this one)
                msgId.topic().subject(),
                msgId.topic().sourceId(),
                msgId.timestamp(),
                message.getData()  // data (message content)
            );
        
        try {
            String encodedMessage = infectionUpdateMsg.encode();
            // Use TCP if available (distributed mode), otherwise use UDP (local mode)
            if (supervisorTcpCommunication != null) {
                supervisorTcpCommunication.sendMessage(supervisorAddress, encodedMessage);
            } else {
                communication.sendMessage(supervisorAddress, encodedMessage);
            }
        } catch (IOException e) {
            System.err.println("Error encoding InfectionUpdateMsg: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void notifyStatusSupervisor(NodeStatus statusToNotify, SpreadMsg message) {
        notifyStatusSupervisor(statusToNotify, message, id);
    }

    public void storeMessage(SpreadMsg message, NodeRole role) {
        storeMessage(message, role, message.getOriginId());
    }

    public void storeMessage(SpreadMsg message, NodeRole role, int infectingNodeId) {

        MessageId msgId = message.getId();
        long receivedTimeStamp = msgId.timestamp();
        String receivedSubject = msgId.topic().subject();
        int sourceId = msgId.topic().sourceId();

        StatusForMessage newMessage = new StatusForMessage(message, role);

        storedMessages.entrySet().removeIf(entry -> {
            MessageId key = entry.getKey();
            return key.topic().subject().equals(receivedSubject) && key.topic().sourceId() == sourceId;
        });

        storedMessages.put(msgId, newMessage);

        if (isRunning) {
            if(role == NodeRole.FORWARDER) {
            System.out.println("[Node " + id + "] Stored/Updated subject '" + receivedSubject +
                    "' with value: " + message.getData() + " (timestamp: " + receivedTimeStamp + ", sourceId: " + sourceId + ")");
            } else if(role == NodeRole.SOURCE) {
                System.out.println("[Node " + id + "] Generated as SOURCE - subject '" + assignedSubjectAsSource +
                        "' with value: " +  message.getData());
            }
        }

        int actualInfectingNodeId;
        if(role == NodeRole.SOURCE) {
            actualInfectingNodeId = id;
        }
        else {
            actualInfectingNodeId = infectingNodeId;
        }
        notifyStatusSupervisor(NodeStatus.INFECTED, message, actualInfectingNodeId);

    }

    public boolean storeOrIgnoreMessage(SpreadMsg receivedMessage) {
        return storeOrIgnoreMessage(receivedMessage, NodeRole.FORWARDER);
    }

    public boolean storeOrIgnoreMessage(SpreadMsg receivedMessage, NodeRole role) {

        MessageId msgId = receivedMessage.getId();
        String subject = msgId.topic().subject();
        int sourceId = msgId.topic().sourceId();

        StatusForMessage alrStoredMsg = getMessagebySubjectAndSource(subject, sourceId);

        if (alrStoredMsg == null || msgId.timestamp() > alrStoredMsg.getMessage().getId().timestamp()) {
            NodeRole roleToUse = role;

            if (role == NodeRole.FORWARDER && alrStoredMsg != null && alrStoredMsg.getNodeRole() == NodeRole.SOURCE) {
                roleToUse = NodeRole.SOURCE;
            }

            int infectingNodeId = receivedMessage.getOriginId();
            storeMessage(receivedMessage, roleToUse, infectingNodeId);

            return true;
        }

        return false;
    }

    private void generateAndStoreMessage() {
        String data = randomDataGenerator(assignedSubjectAsSource);
        MessageTopic topic = new MessageTopic(assignedSubjectAsSource, id);
        MessageId messageId = new MessageId(topic, 0);
        SpreadMsg message = new SpreadMsg(
            Direction.node_to_node.toString(),
            NodeToNodeMessageType.spread.toString(),
            messageId.topic().subject(),
            messageId.topic().sourceId(),
            messageId.timestamp(),
            id, // originId
            data
        );

        storeOrIgnoreMessage(message, NodeRole.SOURCE);
    }

    private String randomDataGenerator(String subject) {
        Random rand  = new Random();
        int num = rand.nextInt(100);
        return Integer.toString(num);
    }

    public List<SpreadMsg> getAllStoredMessages() {
        List<SpreadMsg> messages = new ArrayList<>();

        for (Map.Entry<MessageId, StatusForMessage> entry : storedMessages.entrySet()) {
            SpreadMsg message = entry.getValue().getMessage();
            messages.add(message);
        }

        return messages;
    }

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
    public List<MessageTopic> getSubscribedTopics() { 
        return new ArrayList<>(subscribedTopics); // Return defensive copy
    }

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
        if (isRunning && !storedMessages.isEmpty()) {
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

    public void stop() {
        this.isRunning = false;
    }

    public boolean isRunning() {
        return isRunning;
    }

}
