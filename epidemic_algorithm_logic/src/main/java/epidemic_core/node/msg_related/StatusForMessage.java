package epidemic_core.node.msg_related;

import epidemic_core.message.node_to_node.spread.SpreadMsg;

// Represents the status and role of a node relative to a specific message.
// Each node can have different roles (SOURCE or FORWARDER) for different messages.

public class StatusForMessage {

    private final SpreadMsg message;
    private NodeStatus nodeStatus;  // INFECTED or REMOVED
    private int k;
    private NodeRole nodeRole;      // SOURCE (generates) or FORWARDER (relays)

    public StatusForMessage(SpreadMsg msg, NodeRole initialRole) {
        this.message = msg;
        this.nodeStatus = NodeStatus.INFECTED;
        k = 0;
        this.nodeRole = initialRole;
    }

    // Getters
    public SpreadMsg getMessage() { return message; }
    public NodeStatus getNodeStatus() {
        return nodeStatus;
    }
    public NodeRole getNodeRole() {
        return nodeRole;
    }
    public int getRedundantCounter() { return k; }

    // Setters
    public void setNodeStatus(NodeStatus nodeStatus) {
        this.nodeStatus = nodeStatus;
    }
    public void setNodeRole(NodeRole nodeRole) {
        this.nodeRole = nodeRole;
    }

    // general Methods
    public void tossCoin() {
        // ...
    }
}