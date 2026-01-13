package supervisor.network_emulation.neighbors_and_subject;

import supervisor.network_emulation.utils.Subjects;
import java.util.*;

/**
 * Selection of source nodes IDs and assignment of subjects
 */

public class NetworkStructureManager {
    private final Map<Integer, List<Integer>> adjMap;
    private final Map<Integer, String> nodeIdToSubject;
    private final Set<Integer> sourceNodesId;
    
    public NetworkStructureManager(Map<Integer, List<Integer>> adjMap, Integer numSourceNodes, Integer totalNodes) {
        this.adjMap = new HashMap<>(adjMap);
        this.nodeIdToSubject = new HashMap<>();
        this.sourceNodesId = new HashSet<>();
        
        selectSourceNodes(numSourceNodes, totalNodes);
        assignSubjectsToSourceNodes();
    }
    
    // Selects source nodes ids randomly
    private void selectSourceNodes(Integer numSourceNodes, Integer totalNodes) {
        Random random = new Random();
        
        while (sourceNodesId.size() < numSourceNodes) {
            int nodeId = random.nextInt(totalNodes);
            sourceNodesId.add(nodeId);
        }
    }
    
    // Assigns subjects randomly to source nodes.
    private void assignSubjectsToSourceNodes() {
        Subjects[] allSubjects = Subjects.values();
        Random random = new Random();
        
        for (Integer nodeId : sourceNodesId) {
            Subjects subject = allSubjects[random.nextInt(allSubjects.length)];
            nodeIdToSubject.put(nodeId, subject.name());
        }
    }
    
    public String getSubjectForNode(Integer nodeId) {
        return nodeIdToSubject.get(nodeId); // returns null if the node is not a source node
    }
    
    public boolean isSourceNode(Integer nodeId) {
        return sourceNodesId.contains(nodeId);
    }
    
    public Set<Integer> getSourceNodesId() {
        return new HashSet<>(sourceNodesId);
    }

    public List<Integer> getNeighbors(Integer nodeId) {
        return adjMap.getOrDefault(nodeId, Collections.emptyList());
    }
    
    // Returns the structural info of a node (neighbors + subject)
    public NodeStructuralInfo getNodeStructuralInfo(Integer nodeId) {
        List<Integer> neighbors = getNeighbors(nodeId);
        String subject = getSubjectForNode(nodeId);
        return new NodeStructuralInfo(nodeId, neighbors, subject);
    }
    
    // Returns a map with the structural info of all nodes
    public Map<Integer, NodeStructuralInfo> getAllNodesStructuralInfo() {
        Map<Integer, NodeStructuralInfo> result = new HashMap<>();
        
        for (Integer nodeId : adjMap.keySet()) {
            result.put(nodeId, getNodeStructuralInfo(nodeId));
        }
        
        return result;
    }
   
    public StructuralInfosMatrix getStructuralInfosMatrix() {
        return new StructuralInfosMatrix(adjMap, nodeIdToSubject);
    }
}
