package epidemic_core.node.mode.pull;

import epidemic_core.node.general.Address;
import epidemic_core.node.general.Node;

import java.util.List;
import java.util.Map;

public class PullNode extends Node {

    public PullNode(Integer id,
                    List<Integer> neighbours,
                    Map<Integer, Address> nodeIdToAddress,
                    List<Map<String, SubjectState>> subjects) {

        super(id, neighbours, nodeIdToAddress, subjects);

    }
}
