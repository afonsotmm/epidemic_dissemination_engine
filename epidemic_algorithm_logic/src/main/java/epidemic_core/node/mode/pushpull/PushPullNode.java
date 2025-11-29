package epidemic_core.node.mode.pushpull;

import general.communication.utils.Address;
import epidemic_core.node.Node;
import supervisor.NodeIdToAddressTable;

import java.util.List;
import java.util.Map;


public class PushPullNode extends Node {

    public PushPullNode(Integer id,
                    List<Integer> neighbours,
                    String assignedSubjectAsSource,
                    NodeIdToAddressTable nodeIdToAddressTable,
                    Address supervisorAddress) {

        super(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, supervisorAddress);

    }
}