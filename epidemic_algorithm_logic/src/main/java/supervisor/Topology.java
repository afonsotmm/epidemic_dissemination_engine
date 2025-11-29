package src.main.java.supervisor;

import java.util.*;
import java.util.HashMap;

public class Topology {

    // private Integer n; // number of nodes
    public static Map<Integer, List<Integer>> createTopology(String type, Integer N, Integer k) // type of topology chosen and number of nodes
    {
        return switch (type.toLowerCase()) {
            case "full mesh" -> createMesh(N);
            case "partial mesh" -> createPartialMesh(N);
            case "ring" -> createRing(N);
            case "star" -> createStar(N);
            default -> throw new IllegalStateException("Unexpected value: " + type.toLowerCase());
        };
    }

    // ========== Mesh Topology ==========
    private static Map<Integer, List<Integer>> createMesh(Integer N) {
        Map<Integer, List<Integer>> adjMap = new HashMap<>();

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
    private static Map<Integer, List<Integer>> createPartialMesh(int N) {
        Map<Integer, List<Integer>> adjMap = new HashMap<>();
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
    private static Map<Integer, List<Integer>> createRing(Integer N) {
        Map<Integer, List<Integer>> adjMap = new HashMap<>();

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
    private static Map<Integer, List<Integer>> createStar(Integer N) {
        Map<Integer, List<Integer>> adjMap = new HashMap<>();

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




}
