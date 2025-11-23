package epidemic_core.node.msg_related;

import epidemic_core.message.Message;

public class StatusForMessage {

    private final Message msg;
    private NodeStatus nodeStatus;

    public StatusForMessage(Message msg) {
        this.msg = msg;
        this.nodeStatus = NodeStatus.INFECTED;
    }

    public Message getMessage() {
        return msg;
    }

    public NodeStatus getNodeStatus() {
        return nodeStatus;
    }

    public void setNodeStatus(NodeStatus nodeStatus) {
        this.nodeStatus = nodeStatus;
    }
}