package epidemic_core.message.node_to_supervisor.hello;

import epidemic_core.message.common.Direction;
import epidemic_core.message.node_to_supervisor.NodeToSupervisorMessageType;

public record HelloHeader() {

    public Direction direction() {
        return Direction.node_to_supervisor;
    }

    public NodeToSupervisorMessageType messageType() {
        return NodeToSupervisorMessageType.hello;
    }
}
