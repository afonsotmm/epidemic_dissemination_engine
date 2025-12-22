package supervisor.communication;

import epidemic_core.message.msgTypes.HeaderType;
import epidemic_core.message.Message;

import java.util.concurrent.BlockingQueue;

public class Dispatcher {
    private BlockingQueue<String> msgsQueue;
    private BlockingQueue<String> nodeQueue;
    private BlockingQueue<String> uiQueue;

    public Dispatcher(BlockingQueue<String> msgsQueue, BlockingQueue<String> nodeQueue, BlockingQueue<String> uiQueue) {
        this.msgsQueue = msgsQueue;
        this.nodeQueue = nodeQueue;
        this.uiQueue = uiQueue;
    }

    public void dispatchingLoop(){
        try {
            String consumedMsg = msgsQueue.take();
            String header = Message.getMessageHeader(consumedMsg);

            if(HeaderType.UI_START.name().equals(header)){ //from UI
                uiQueue.put(consumedMsg);
            }
            else if(HeaderType.UI_END.name().equals(header)){ //from UI
                uiQueue.put(consumedMsg);
            }
            else if(HeaderType.ACK.name().equals(header) || HeaderType.NODE.name().equals(header)){ // from Node
                nodeQueue.put(consumedMsg);
            }

            Thread.onSpinWait();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }


}
