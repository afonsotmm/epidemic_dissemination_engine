package epidemic_core.message.node_to_node.feedback;

import epidemic_core.message.common.Direction;
import epidemic_core.message.node_to_node.NodeToNodeMessageType;

public record FeedbackHeader() {
    public Direction direction() { return Direction.node_to_node; }
    public NodeToNodeMessageType messageType() { return NodeToNodeMessageType.feedback; }
}