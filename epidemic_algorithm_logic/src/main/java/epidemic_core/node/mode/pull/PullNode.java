package epidemic_core.node.mode.pull;

import general.communication.utils.Address;
import epidemic_core.node.Node;

import java.util.List;
import java.util.Map;

public class PullNode extends Node {

    public PullNode(Integer id,
                    List<Integer> neighbours,
                    String assignedSubjectAsSource,
                    Map<Integer, Address> nodeIdToAddressTable,
                    Address supervisorAddress) {

        super(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, supervisorAddress);

    }
}
