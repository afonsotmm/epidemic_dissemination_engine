package epidemic_core.node.mode.pull.general.components;

import epidemic_core.node.Node;
import general.communication.Communication;

import java.util.concurrent.BlockingQueue;

public class Listener {

    private BlockingQueue<String> msgsQueue;
    private Communication communication;
    private volatile boolean running;

    public Listener(Node node, BlockingQueue<String> msgsQueue) {
        this.communication = node.getCommunication();
        this.msgsQueue = msgsQueue;

        running = true;
    }

    public void listeningLoop() {
        while(running) {
            String receivedMsg = communication.receiveMessage();
            if (receivedMsg != null) {
                try {
                    msgsQueue.put(receivedMsg);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            Thread.onSpinWait();
        }
    }

    public void stopListening() {
        running = false;
    }

}

