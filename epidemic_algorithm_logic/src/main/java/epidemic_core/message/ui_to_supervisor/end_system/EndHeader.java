package epidemic_core.message.ui_to_supervisor.end_system;

import epidemic_core.message.common.Direction;
import epidemic_core.message.ui_to_supervisor.UiToSupervisorMessageType;

public record EndHeader() {

    public Direction direction() {
        return Direction.ui_to_supervisor;
    }

    public UiToSupervisorMessageType messageType() {
        return UiToSupervisorMessageType.end_system;
    }

}
