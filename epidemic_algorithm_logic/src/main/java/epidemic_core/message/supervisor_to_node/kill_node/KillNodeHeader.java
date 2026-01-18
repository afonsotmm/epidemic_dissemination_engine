package epidemic_core.message.supervisor_to_node.kill_node;

import epidemic_core.message.common.Direction;
import epidemic_core.message.supervisor_to_node.SupervisorToNodeMessageType;

public record KillNodeHeader() {

    public Direction direction() {
        return Direction.supervisor_to_node;
    }

    public SupervisorToNodeMessageType messageType() {
        return SupervisorToNodeMessageType.kill_node;
    }
}
