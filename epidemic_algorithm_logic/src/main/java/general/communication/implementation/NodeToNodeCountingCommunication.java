package general.communication.implementation;

import epidemic_core.message.common.MessageDispatcher;
import epidemic_core.message.common.NodeToNodeMessageCounter;
import general.communication.Communication;
import general.communication.utils.Address;

public final class NodeToNodeCountingCommunication implements Communication {

    private final Communication delegate;

    public NodeToNodeCountingCommunication(Communication delegate) {
        this.delegate = delegate;
    }

    @Override
    public void setupSocket(Address myAddress) {
        delegate.setupSocket(myAddress);
    }

    @Override
    public void sendMessage(Address destination, String message) {
        if (message != null && MessageDispatcher.isNodeToNode(message)) {
            NodeToNodeMessageCounter.getInstance().increment();
        }
        delegate.sendMessage(destination, message);
    }

    @Override
    public String receiveMessage() {
        return delegate.receiveMessage();
    }

    @Override
    public void closeSocket() {
        delegate.closeSocket();
    }
}
