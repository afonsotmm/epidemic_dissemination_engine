package epidemic_core.node.mode.pull.components;

import epidemic_core.message.Message;
import epidemic_core.message.msgTypes.NodeToNode;

import java.util.concurrent.BlockingQueue;

public class Dispatcher {

    private BlockingQueue<String> msgsQueue;
    private BlockingQueue<String> replyMsgs;
    private BlockingQueue<String> requestMsgs;

    private volatile boolean running;

    public Dispatcher(BlockingQueue<String> msgsQueue, BlockingQueue<String> replyMsgs, BlockingQueue<String> requestMsgs) {
        this.msgsQueue = msgsQueue;
        this.replyMsgs = replyMsgs;
        this.requestMsgs = requestMsgs;

        running = true;
    }

    public void dispatchingLoop(){

        while(running){
            try {
                String consumedMsg = msgsQueue.take();
                String header = Message.getMessageHeader(consumedMsg);

                if(NodeToNode.REQUEST.name().equals(header)){
                    requestMsgs.put(consumedMsg);
                }
                else if(NodeToNode.REPLY.name().equals(header)){
                    replyMsgs.put(consumedMsg);
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
