package supervisor;

import java.util.*;
import general.communication.utils.Address;

public class NodeIdToAddressTable
{
    private final Map<Integer, Address> table;

    public NodeIdToAddressTable(int N) {
        this.table = new HashMap<>();
        Set<String> usedIps = new HashSet<>();
        Random random = new Random();

        for (int id = 0; id < N; id++) {
            String ip;
            do {
                int b = random.nextInt(256);
                int c = random.nextInt(256);
                int d = random.nextInt(256);
                ip = "127." + b + "." + c + "." + d;
            } while (usedIps.contains(ip));

            usedIps.add(ip);

            int port = 8000;
            table.put(id, new Address(ip, port));
        }
    }

    public Address get(int nodeId) {
        return table.get(nodeId);
    }

    public Map<Integer, Address> getAll() {
        return table;
    }

    public void put(int i, Address localhost) {
    }
}