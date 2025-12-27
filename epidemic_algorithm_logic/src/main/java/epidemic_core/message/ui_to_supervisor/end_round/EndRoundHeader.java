package epidemic_core.message.ui_to_supervisor.end_round;

import epidemic_core.message.common.Direction;
import epidemic_core.message.ui_to_supervisor.UiToSupervisorMessageType;

public record EndRoundHeader() {

    public Direction direction() {
        return Direction.ui_to_supervisor;
    }

    public UiToSupervisorMessageType messageType() {
        return UiToSupervisorMessageType.start_round;
    }

}
