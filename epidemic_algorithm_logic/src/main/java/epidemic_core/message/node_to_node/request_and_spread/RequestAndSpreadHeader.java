package epidemic_core.message.node_to_node.request_and_spread;

import epidemic_core.message.common.Direction;
import epidemic_core.message.node_to_node.NodeToNodeMessageType;

public record RequestAndSpreadHeader() {

    public Direction direction() {
        return Direction.node_to_node;
    }

    public NodeToNodeMessageType messageType() {
        return NodeToNodeMessageType.request_and_spread;
    }
}
