package epidemic_core.message.node_to_node.initial_request;

import epidemic_core.message.common.Direction;
import epidemic_core.message.node_to_node.NodeToNodeMessageType;

public record InitialRequestHeader() {


    public Direction direction() {
        return Direction.node_to_node;
    }

    public NodeToNodeMessageType messageType() {
        return NodeToNodeMessageType.initial_request;
    }

}
