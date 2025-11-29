package epidemic_core.node;

import epidemic_core.message.Message;
import epidemic_core.message.MessageType;
import epidemic_core.node.msg_related.NodeRole;
import epidemic_core.node.msg_related.NodeStatus;
import epidemic_core.node.msg_related.StatusForMessage;
import general.communication.Communication;
import general.communication.implementation.TcpCommunication;
import general.communication.implementation.UdpCommunication;
import general.communication.utils.Address;
import supervisor.NodeIdToAddressTable;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

//TODO: URGENT Refactor this!!!!

public class Node {

    private Integer id;
    private List<Integer> neighbours;
    private String assignedSubjectAsSource; // e.g: "temperature"; "none" (if it's not source of any msg) etc
    private NodeIdToAddressTable nodeIdToAddressTable; // also includes the supervisor address
    private Address supervisorAddress;
    private Communication communication;

    // Stored by Subject and includes the Status & Role of the node relative to that msg
    private Map<String, StatusForMessage> storedMessages;

    // Constructor
    public Node(Integer id,
                List<Integer> neighbours,
                String assignedSubjectAsSource,
                NodeIdToAddressTable nodeIdToAddressTable,
                Address supervisorAddress) {
        this.id = id;
        this.neighbours = neighbours;
        this.assignedSubjectAsSource = assignedSubjectAsSource;
        this.nodeIdToAddressTable = nodeIdToAddressTable;
        this.supervisorAddress = supervisorAddress;
        this.storedMessages = new ConcurrentHashMap<>();
        // TODO: Supervisor should choose for each Node either Udp or Tcp
        this.communication = new UdpCommunication();
        // this.communication = new TcpCommunication();

        // Initialize the socket to listen for incoming messages
        Address myAddress = nodeIdToAddressTable.get(id);
        if (myAddress != null) {
            this.communication.startListening(myAddress);
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

    // Actuating as a FORWARDER of a given msg
    // TODO: Refactor this duplication
    public Boolean storeOrIgnoreMessage(Message receivedMessage) {

        String receivedSubject = receivedMessage.getSubject();
        Integer receivedTimeStamp = receivedMessage.getTimeStamp();

        if (hasMessage(receivedSubject)) {

            Message currMessage = storedMessages.get(receivedSubject).getMessage();
            Integer currTimeStamp = currMessage.getTimeStamp();

            if (receivedTimeStamp > currTimeStamp) {
                StatusForMessage newMessage = new StatusForMessage(receivedMessage, NodeRole.FORWARDER);
                storedMessages.put(receivedSubject, newMessage);

                // Log: Node stored/updated message
                System.out.println("[Node " + id + "] Stored/Updated subject '" + receivedSubject + 
                        "' with value: " + receivedMessage.getData() + " (timestamp: " + receivedTimeStamp + ")");

                // Node is now INFECTED with the received msg so supervisor needs to know!
                // TODO: Make the msg modular (here we are creating a new msg type...)
                // Format: INFECTED;nodeId;subject;timestamp;data
                String encodedMessage = MessageType.INFECTED + ";" + id + ";" + 
                        receivedMessage.getSubject() + ";" + 
                        receivedMessage.getTimeStamp() + ";" + 
                        receivedMessage.getData();
                communication.sendMessage(supervisorAddress, encodedMessage);

                // Store
                return true;
            }

        } else {
            StatusForMessage newMessage = new StatusForMessage(receivedMessage, NodeRole.FORWARDER);
            storedMessages.put(receivedSubject, newMessage);

            // Log: Node stored new message
            System.out.println("[Node " + id + "] Stored new subject '" + receivedSubject + 
                    "' with value: " + receivedMessage.getData() + " (timestamp: " + receivedTimeStamp + ")");

            // Node is now INFECTED with the received msg so supervisor needs to know!
            // Format: INFECTED;nodeId;subject;timestamp;data
            String encodedMessage = MessageType.INFECTED + ";" + id + ";" + 
                    receivedMessage.getSubject() + ";" + 
                    receivedMessage.getTimeStamp() + ";" + 
                    receivedMessage.getData();
            communication.sendMessage(supervisorAddress, encodedMessage);

            // Store
            return true;
        }

        // Ignore
        return false;
    }

    // Actuating as a SOURCE of a given msg
    private void generateAndStoreMessage() {
        String data = randomDataGenerator(assignedSubjectAsSource);
        Message message = new Message(assignedSubjectAsSource, 0, data);
        StatusForMessage generatedMessage = new StatusForMessage(message, NodeRole.SOURCE);
        storedMessages.put(assignedSubjectAsSource, generatedMessage);
        
        // Log: Node generated message as SOURCE
        System.out.println("[Node " + id + "] Generated as SOURCE - subject '" + assignedSubjectAsSource + 
                "' with value: " + data);
    }

    private String randomDataGenerator(String subject) {
        Random rand  = new Random();
        int num = rand.nextInt(100); // TODO: Make this dependent on the subject
        return Integer.toString(num);
    }

    // Get all the stored messages
    public List<Message> getAllStoredMessages() {
        List<Message> messages = new ArrayList<>();

        for (Map.Entry<String, StatusForMessage> entry : storedMessages.entrySet()) {
            String subject = entry.getKey();
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

    // Get id
    public Integer getId() {
        return id;
    }

}

