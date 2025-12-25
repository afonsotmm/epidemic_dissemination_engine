package epidemic_core.message.supervisor_to_node.start_round;

import epidemic_core.message.common.Direction;
import epidemic_core.message.supervisor_to_node.SupervisorToNodeMessageType;

public record StartRoundHeader() {

    public Direction direction() {
        return Direction.supervisor_to_node;
    }

    public SupervisorToNodeMessageType messageType() {
        return SupervisorToNodeMessageType.start_round;
    }

}



