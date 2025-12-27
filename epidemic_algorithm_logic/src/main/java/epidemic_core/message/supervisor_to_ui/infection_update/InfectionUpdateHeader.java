package epidemic_core.message.supervisor_to_ui.infection_update;

import epidemic_core.message.common.Direction;
import epidemic_core.message.supervisor_to_ui.SupervisorToUiMessageType;

public record InfectionUpdateHeader() {

    public Direction direction() {
        return Direction.supervisor_to_node;
    }

    public SupervisorToUiMessageType messageType() {
        return SupervisorToUiMessageType.infection_update;
    }

}
