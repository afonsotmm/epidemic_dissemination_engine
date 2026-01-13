package epidemic_core.message.supervisor_to_ui.remotion_update;

import epidemic_core.message.common.Direction;
import epidemic_core.message.supervisor_to_ui.SupervisorToUiMessageType;

public record RemotionUpdateHeader() {

    public Direction direction() {
        return Direction.supervisor_to_ui;
    }

    public SupervisorToUiMessageType messageType() {
        return SupervisorToUiMessageType.remotion_update;
    }

}
