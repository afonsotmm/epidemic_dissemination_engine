package epidemic_core.node.mode.push.components;

import epidemic_core.node.mode.push.PushNode;
import general.communication.Communication;

import java.util.concurrent.BlockingQueue;

public class Listener {

    private BlockingQueue<String> receivedMsgsQueue;
    private Communication communication;
    private volatile boolean running;

    public Listener(PushNode node, BlockingQueue<String> receivedMsgsQueue) {
        this.communication = node.getCommunication();
        this.receivedMsgsQueue = receivedMsgsQueue;

        running = true;
    }

    public void listeningLoop() {
        while(running) {
            String receivedMsg = communication.receiveMessage();
            if (receivedMsg != null) {
                try {
                    receivedMsgsQueue.put(receivedMsg);
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
