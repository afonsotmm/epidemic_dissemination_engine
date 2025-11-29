package src.main.java.supervisor;
package general.communication.utils;

import java.util.*;

public class nodeIdToAddressTable
{
    private final Map<Integer, Address> table;

    public nodeIdToAddressTable(int N) {
        this.table = new HashMap<>();

        for (int id = 0; id < N; id++) {
            String ip = "127.0.0.1";     // localhost
            int port = 8000 + id;        // port
            table.put(id, new Address(ip, port));
        }
    }

    public Address get(int nodeId) {
        return table.get(nodeId);
    }

    public Map<Integer, Address> getAll() {
        return table;
    }
}