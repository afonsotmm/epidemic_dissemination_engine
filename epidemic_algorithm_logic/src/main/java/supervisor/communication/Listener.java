package supervisor.communication;

import general.communication.Communication;
import supervisor.Supervisor;

import java.util.concurrent.BlockingQueue;

public class Listener {
    private BlockingQueue<String> msgsQueue;
    private Communication communication;

    public Listener(Supervisor supervisor, BlockingQueue<String> msgsQueue) {
        this.communication = supervisor.getCommunication();
        this.msgsQueue = msgsQueue;
    }

    public void listeningLoop() {
        String receivedMsg = communication.receiveMessage();
        if (receivedMsg != null) {
            try {
                msgsQueue.put(receivedMsg);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Thread.onSpinWait();
    }
}
