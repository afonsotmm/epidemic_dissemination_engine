package epidemic_core.message.common;

public enum Direction {
    // Node to Node
    node_to_node,

    // Supervisor To/From Node
    supervisor_to_node,
    node_to_supervisor,

    // Supervisor To/From UI
    ui_to_supervisor,
    supervisor_to_ui
}
