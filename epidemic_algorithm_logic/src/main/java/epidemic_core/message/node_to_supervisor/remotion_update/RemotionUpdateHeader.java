package epidemic_core.message.node_to_supervisor.remotion_update;

import epidemic_core.message.common.Direction;
import epidemic_core.message.node_to_supervisor.NodeToSupervisorMessageType;

public record RemotionUpdateHeader() {

    public Direction direction() {
        return Direction.node_to_supervisor;
    }

    public NodeToSupervisorMessageType messageType() {
        return NodeToSupervisorMessageType.remotion_update;
    }

}
