package epidemic_core.message.ui_to_supervisor.start_system;

import epidemic_core.message.common.Direction;
import epidemic_core.message.ui_to_supervisor.UiToSupervisorMessageType;
import general.communication.utils.Address;

public record StartHeader() {

    public Direction direction() {
        return Direction.ui_to_supervisor;
    }

    public UiToSupervisorMessageType messageType() {
        return UiToSupervisorMessageType.start_system;
    }

    public static Address parse(String value) {
        String[] parts = value.split(":");
        String ip = parts[0];
        int port = Integer.parseInt(parts[1]);
        return new Address(ip, port);
    }
}
