package epidemic_core.node;

import epidemic_core.message.Message;
import epidemic_core.node.msg_related.NodeRole;
import epidemic_core.node.msg_related.StatusForMessage;
import general.communication.Communication;
import general.communication.implementation.TcpCommunication;
import general.communication.implementation.UdpCommunication;
import general.communication.utils.Address;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

//TODO: Refactor this!!!!

public class Node {

    private Integer id;
    private List<Integer> neighbours;
    private String assignedSubjectAsSource; // e.g: "temperature"; "none" (if it's not source of any msg) etc
    private Map<Integer, Address> nodeIdToAddressTable;
    private Communication communication;

    // Stored by Subject and includes the Status & Role of the node relative to that msg
    private Map<String, StatusForMessage> storedMessages;

    // Constructor
    public Node(Integer id,
                List<Integer> neighbours,
                String assignedSubjectAsSource,
                Map<Integer, Address> nodeIdToAddressTable) {
        this.id = id;
        this.neighbours = neighbours;
        this.assignedSubjectAsSource = assignedSubjectAsSource;
        this.nodeIdToAddressTable = nodeIdToAddressTable;
        this.storedMessages = new ConcurrentHashMap<>();
        // TODO: Supervisor should choose for each Node wither Udp or Tcp
        this.communication = new UdpCommunication();
        // this.communication = new TcpCommunication();

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
    public Boolean storeOrIgnoreMessage(Message receivedMessage) {

        String receivedSubject = receivedMessage.getSubject();
        Integer receivedTimeStamp = receivedMessage.getTimeStamp();

        if (hasMessage(receivedSubject)) {

            Message currMessage = storedMessages.get(receivedSubject).getMessage();
            Integer currTimeStamp = currMessage.getTimeStamp();

            if (receivedTimeStamp > currTimeStamp) {
                StatusForMessage newMessage = new StatusForMessage(receivedMessage, NodeRole.FORWARDER);
                storedMessages.put(receivedSubject, newMessage);
                // Store
                return true;
            }

        } else {
            StatusForMessage newMessage = new StatusForMessage(receivedMessage, NodeRole.FORWARDER);
            storedMessages.put(receivedSubject, newMessage);
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

}

