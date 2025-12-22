package epidemic_core.node.mode.push.components;

import epidemic_core.message.Message;
import epidemic_core.message.msgTypes.NodeToNode;

import java.util.concurrent.BlockingQueue;

public class Dispatcher {

    private BlockingQueue<String> receivedMsgsQueue;
    private BlockingQueue<String> pushMsgs;
    private volatile boolean running;

    public Dispatcher(BlockingQueue<String> receivedMsgsQueue, BlockingQueue<String> pushMsgs) {
        this.receivedMsgsQueue = receivedMsgsQueue;
        this.pushMsgs = pushMsgs;

        running = true;
    }

    public void dispatchingLoop(){

        while(running){
            try {
                String consumedMsg = receivedMsgsQueue.take();
                String header = Message.getMessageHeader(consumedMsg);

                if(NodeToNode.PUSH.name().equals(header)){
                    pushMsgs.put(consumedMsg);
                }

                Thread.onSpinWait();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

    }

    public void stopDispatching(){
        running = false;
    }

}
