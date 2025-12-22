package epidemic_core.node;

import epidemic_core.message.Message;
import epidemic_core.node.msg_related.NodeRole;
import epidemic_core.node.msg_related.NodeStatus;
import epidemic_core.node.msg_related.StatusForMessage;
import general.communication.Communication;
import general.communication.implementation.UdpCommunication;
import general.communication.utils.Address;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class Node {

    private Integer id;
    private List<Integer> neighbours;
    private String assignedSubjectAsSource; // e.g: "temperature"; "none" (if it's not source of any msg) etc
    private Map<Integer, Address> nodeIdToAddressTable; // also includes the supervisor address
    private Address supervisorAddress;
    private Communication communication;

    // Stored by Subject and includes the Status & Role of the node relative to that msg
    // TODO: Change the input to: source_id + local_counter + subject
    private Map<String, StatusForMessage> storedMessages;

    // Constructor
    public Node(Integer id,
                List<Integer> neighbours,
                String assignedSubjectAsSource,
                Map<Integer, Address> nodeIdToAddressTable,
                Address supervisorAddress) {

        this.id = id;
        this.neighbours = neighbours;
        this.assignedSubjectAsSource = assignedSubjectAsSource;
        this.nodeIdToAddressTable = nodeIdToAddressTable;
        this.supervisorAddress = supervisorAddress;
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
    public Boolean hasMessage(String subject) { return storedMessages.containsKey(subject); }

    public StatusForMessage getMessagebySubject(String subject) { return storedMessages.get(subject); }

    public List<Integer> getNeighbours() { return neighbours; }

    public Address getNeighbourAddress(Integer id) { return nodeIdToAddressTable.get(id); }

    public Communication getCommunication() { return communication; }

    public Integer getId() { return id; }

    // Sends a notification to supervisor about the Node's current status related to a given message
    public void notifyStatusSupervisor(NodeStatus statusToNotify, Message message) {

        // Create a new header (like: "StatusUpdate")
        String encodedMessage = statusToNotify + ";" + id + ";" +
                message.getSubject() + ";" +
                message.getTimeStamp() + ";" +
                message.getData();
        communication.sendMessage(supervisorAddress, encodedMessage);

    }

    // Stores the message
    public void storeMessage(Message message, NodeRole role) {

        Integer receivedTimeStamp = message.getTimeStamp();
        String receivedSubject = message.getSubject();

        StatusForMessage newMessage = new StatusForMessage(message, role);
        storedMessages.put(receivedSubject, newMessage);

        if(role == NodeRole.FORWARDER) {
        // Log: Node stored/updated message
        System.out.println("[Node " + id + "] Stored/Updated subject '" + receivedSubject +
                "' with value: " + message.getData() + " (timestamp: " + receivedTimeStamp + ")");
        } else if(role == NodeRole.SOURCE) {
            // Log: Node generated message as SOURCE
            System.out.println("[Node " + id + "] Generated as SOURCE - subject '" + assignedSubjectAsSource +
                    "' with value: " +  message.getData());
        }

        notifyStatusSupervisor(NodeStatus.INFECTED, message);

    }

    // Stores the message only if it is new or more recent than the stored one
    // Assumes FORWARDER role by default (when receiving from another node)
    public boolean storeOrIgnoreMessage(Message receivedMessage) {
        return storeOrIgnoreMessage(receivedMessage, NodeRole.FORWARDER);
    }

    public boolean storeOrIgnoreMessage(Message receivedMessage, NodeRole role) {

        StatusForMessage alrStoredMsg = storedMessages.get(receivedMessage.getSubject());

        if (alrStoredMsg == null || receivedMessage.getTimeStamp() > alrStoredMsg.getMessage().getTimeStamp()) {
            NodeRole roleToUse = role;
            
            // If role is SOURCE, always use SOURCE
            // If role is FORWARDER but node was already SOURCE, maintain SOURCE
            if (role == NodeRole.FORWARDER && alrStoredMsg != null && alrStoredMsg.getNodeRole() == NodeRole.SOURCE) {
                roleToUse = NodeRole.SOURCE;
            }
            // Otherwise, use the provided role (SOURCE or FORWARDER)
            storeMessage(receivedMessage, roleToUse);
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
        Message message = new Message(assignedSubjectAsSource, 0, data);

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
    public List<Message> getAllStoredMessages() {
        List<Message> messages = new ArrayList<>();

        for (Map.Entry<String, StatusForMessage> entry : storedMessages.entrySet()) {
            Message message = entry.getValue().getMessage();
            messages.add(message);
        }

        return messages;
    }

    // Print current state of all subjects stored in this node
    public void printNodeState() {
        if (storedMessages.isEmpty()) {
            System.out.println("[Node " + id + "] No subjects stored");
        } else {
            System.out.println("[Node " + id + "] Current subjects:");
            for (Map.Entry<String, StatusForMessage> entry : storedMessages.entrySet()) {
                String subject = entry.getKey();
                Message message = entry.getValue().getMessage();
                NodeRole role = entry.getValue().getNodeRole();
                System.out.println("  - Subject: '" + subject + "' | Value: " + message.getData() + 
                        " | Timestamp: " + message.getTimeStamp() + " | Role: " + role);
            }
        }
    }

}

