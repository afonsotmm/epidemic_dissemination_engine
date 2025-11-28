package src.main.java.supervisor;

import java.util.*;
import java.util.HashMap;

public class Topology {

    // private Integer n; // number of nodes
    public static Map<Integer, List<Integer>> createTopology(String type, Integer N) // type of topology chosen and number of nodes
    {
        return switch (type.toLowerCase()) {
            case "full mesh" -> createMesh(N);
            case "ring" -> createRing(N);
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

}
