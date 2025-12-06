package supervisor.network_emulation.topology_creation;

import java.util.*;
import java.util.HashMap;

public class Topology {

    private Map<Integer, List<Integer>> adjMap;

    // private Integer n; // number of nodes
    public Map<Integer, List<Integer>> createTopology(TopologyType type, Integer N) // type of topology chosen and number of nodes
    {
        return switch (type) {
            case FULL_MESH -> createMesh(N);
            case PARTIAL_MESH -> createPartialMesh(N);
            case RING -> createRing(N);
            case STAR -> createStar(N);
        };
    }

    // ========== Mesh Topology ==========
    private Map<Integer, List<Integer>> createMesh(Integer N) {
        adjMap = new HashMap<>();

        for (int i = 0; i < N; i++) { // add neighbours
            List<Integer> neighbours = new ArrayList<>();

            for (int j = 0; j < N; j++) {
                if (j != i) neighbours.add(j);
            }

            adjMap.put(i, neighbours);
        }
        return adjMap;
    }

    // ========== Partial Mesh Topology ==========
    private Map<Integer, List<Integer>> createPartialMesh(int N) {
        adjMap = new HashMap<>();
        Random random = new Random();

        for (int i = 0; i < N; i++) {
            adjMap.put(i, new ArrayList<>());
        }

        for (int i = 0; i < N; i++) {
            int n_neighbors = random.nextInt(N - 1); // number of neighbours for each node

            while (adjMap.get(i).size() < n_neighbors) {
                int neighbour = random.nextInt(N - 1);
                if (neighbour != i && !adjMap.get(i).contains(neighbour)) {
                    adjMap.get(i).add(neighbour);
                    adjMap.get(neighbour).add(i); // add neighbour to both nodes
                }
            }
        }
        return adjMap;
    }

    // ========== Ring Topology ==========
    private Map<Integer, List<Integer>> createRing(Integer N) {
        adjMap = new HashMap<>();

        for (int i = 0; i < N; i++) {
            List<Integer> neighbours = new ArrayList<>();

            int left = (i - 1 + N) % N;   // left neighbour
            int right = (i + 1) % N;      // right neighbour

            neighbours.add(left);
            neighbours.add(right);

            adjMap.put(i, neighbours);
        }
        return adjMap;
    }

    // ========== Star Topology ==========
    private Map<Integer, List<Integer>> createStar(Integer N) {
        adjMap = new HashMap<>();

        for (int i = 0; i < N; i++) {
            List<Integer> neighbours = new ArrayList<>();

            if(i == 0){ // node 0 is the central node
                for(int j = 1; j < N; j++){
                    neighbours.add(j);
                }
            }
            else neighbours.add(0);

            adjMap.put(i, neighbours);
        }
        return adjMap;
    }

    public List<Integer> get(int nodeId) {
        return adjMap.get(nodeId);
    }

    public Map<Integer, List<Integer>> getAll() {
        return adjMap;
    }

}
