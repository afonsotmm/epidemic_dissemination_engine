package logic.node;

import logic.Address;
import logic.SubjectState;

import java.util.Map;
import java.util.List;

public class Node {

    private Integer id;
    private List<Integer> neighbours;
    private Map<Integer, Address> nodeIdToAddress;
    private List<Map<String, SubjectState>> subjects;

    // Constructor
    public Node(Integer id, List<Integer> neighbours, Map<Integer, Address> nodeIdToAddress, List<Map<String, SubjectState>> subjects) {
        this.id = id;
        this.neighbours = neighbours;
        this.nodeIdToAddress = nodeIdToAddress;
    }

}


