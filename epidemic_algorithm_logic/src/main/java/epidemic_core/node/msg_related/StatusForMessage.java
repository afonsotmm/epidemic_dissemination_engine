package epidemic_core.node.msg_related;

import epidemic_core.message.Message;

// Represents the status and role of a node relative to a specific message.
// Each node can have different roles (SOURCE or FORWARDER) for different messages.

public class StatusForMessage {

    private final Message message;
    private NodeStatus nodeStatus;  // INFECTED or REMOVED
    private NodeRole nodeRole;      // SOURCE (generates) or FORWARDER (relays)

    public StatusForMessage(Message msg, NodeRole initialRole) {
        this.message = msg;
        this.nodeStatus = NodeStatus.INFECTED;
        this.nodeRole = initialRole;
    }

    public Message getMessage() {
        return message;
    }

    // Getters
    public NodeStatus getNodeStatus() {
        return nodeStatus;
    }

    public NodeRole getNodeRole() {
        return nodeRole;
    }

    // Setters
    public void setNodeStatus(NodeStatus nodeStatus) {
        this.nodeStatus = nodeStatus;
    }

    public void setNodeRole(NodeRole nodeRole) {
        this.nodeRole = nodeRole;
    }
}