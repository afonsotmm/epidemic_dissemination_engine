package epidemic_core.node.mode.pushpull.general.components;

import epidemic_core.node.Node;
import general.communication.Communication;

import java.util.concurrent.BlockingQueue;

public class Listener {

    private BlockingQueue<String> receivedMsgsQueue;
    private Node node; // Keep reference to Node to get Communication dynamically
    private volatile boolean running;

    public Listener(Node node, BlockingQueue<String> receivedMsgsQueue) {
        this.node = node; // Keep reference to Node (Communication may be replaced by DistributedNodeStub)
        this.receivedMsgsQueue = receivedMsgsQueue;

        running = true;
    }

    public void listeningLoop() {
        while(running) {
            // Get Communication dynamically from Node (may be replaced by DistributedNodeStub)
            Communication communication = node.getCommunication();
            String receivedMsg = communication.receiveMessage();
            if (receivedMsg != null) {
                try {
                    receivedMsgsQueue.put(receivedMsg);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    public void stopListening() {
        running = false;
    }

}

