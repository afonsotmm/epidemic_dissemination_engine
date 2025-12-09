package epidemic_core.message;
// TODO: Eliminar duplicação com o NodeStatus
public enum MessageType {
    REQUEST,
    REPLY,
    INFECTED,
    REMOVED,
    PUSH
}
