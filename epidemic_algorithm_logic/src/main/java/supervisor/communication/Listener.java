package supervisor.communication;

import general.communication.Communication;
import supervisor.Supervisor;

import java.util.concurrent.BlockingQueue;

/**	
 * Responsible for receiving messages from the communication layer and putting them in the msgsQueue
 */

public class Listener {
    private BlockingQueue<String> msgsQueue;
    private Communication communication;

    public Listener(Supervisor supervisor, BlockingQueue<String> msgsQueue) {
        this.communication = supervisor.getCommunication();
        this.msgsQueue = msgsQueue;
    }

    public void listeningLoop() {
        while (true) {
            String receivedMsg = communication.receiveMessage();
            if (receivedMsg != null) {
                System.out.println("[Supervisor] Received message from UI/Node");
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
}
