package epidemic_core.message.supervisor_to_ui.structural_infos;

import epidemic_core.message.common.Direction;
import epidemic_core.message.supervisor_to_ui.SupervisorToUiMessageType;

public class StructuralInfosHeader {
    public Direction direction() {
        return Direction.supervisor_to_ui;
    }

    public SupervisorToUiMessageType messageType() {
        return SupervisorToUiMessageType.structural_infos;
    }
}
