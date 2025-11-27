package epidemic_core.node.mode.pushpull;

import general.communication.utils.Address;
import epidemic_core.node.Node;

import java.util.List;
import java.util.Map;


public class PushPullNode extends Node {

    public PushPullNode(Integer id,
                    List<Integer> neighbours,
                    Map<Integer, Address> nodeIdToAddress) {

        super(id, neighbours, nodeIdToAddress);

    }
}