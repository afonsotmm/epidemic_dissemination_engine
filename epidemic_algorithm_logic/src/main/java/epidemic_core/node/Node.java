package epidemic_core.node;

import epidemic_core.message.Message;
import epidemic_core.node.msg_related.StatusForMessage;
import general.communication.Address;

import java.util.Map;
import java.util.List;

public class Node {

    private Integer id;
    private List<Integer> neighbours;
    private Map<Integer, Address> nodeIdToAddressTable;
    private Map<String, List<StatusForMessage>> storedMessagesBySubject;

    // Constructor
    public Node(Integer id, List<Integer> neighbours, Map<Integer, Address> nodeIdToAddressTable, Map<String, List<Message>> msgsPerSubject) {
        this.id = id;
        this.neighbours = neighbours;
        this.nodeIdToAddressTable = nodeIdToAddressTable;
    }

}


