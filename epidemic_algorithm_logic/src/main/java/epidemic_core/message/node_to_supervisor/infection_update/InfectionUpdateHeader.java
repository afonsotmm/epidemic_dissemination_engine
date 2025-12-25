package epidemic_core.message.node_to_supervisor.infection_update;

import epidemic_core.message.common.Direction;
import epidemic_core.message.node_to_supervisor.NodeToSupervisorMessageType;

public record InfectionUpdateHeader() {

    public Direction direction() {
        return Direction.node_to_supervisor;
    }

    public NodeToSupervisorMessageType messageType() {
        return NodeToSupervisorMessageType.infection_update;
    }

}
