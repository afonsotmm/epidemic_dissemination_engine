package epidemic_core.message.msgTypes;
// TODO: Eliminar duplicação com o NodeStatus
public enum NodeToNode {
    REQUEST,
    REPLY,
    INFECTED,
    REMOVED,
    PUSH
}
