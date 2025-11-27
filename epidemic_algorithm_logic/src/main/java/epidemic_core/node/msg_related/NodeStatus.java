package epidemic_core.node.msg_related;

// We assume that if a node has info about a message it's automatically infected that's
// why we don't consider the susceptible status. It's either infected or removed.
public enum NodeStatus {
    INFECTED,
    REMOVED
}
