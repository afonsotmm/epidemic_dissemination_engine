package epidemic_core.node.mode.pushpull.components;

import epidemic_core.message.Message;
import epidemic_core.message.MessageType;

import java.util.concurrent.BlockingQueue;

public class Dispatcher {

    private BlockingQueue<String> receivedMsgsQueue;
    private BlockingQueue<String> replyMsgs;
    private BlockingQueue<String> requestMsgs;
    private volatile boolean running;

    public Dispatcher(BlockingQueue<String> receivedMsgsQueue, BlockingQueue<String> replyMsgs, BlockingQueue<String> requestMsgs) {
        this.receivedMsgsQueue = receivedMsgsQueue;
        this.replyMsgs = replyMsgs;
        this.requestMsgs = requestMsgs;

        running = true;
    }

    public void dispatchingLoop(){

        while(running){
            try {
                String consumedMsg = receivedMsgsQueue.take();
                String header = Message.getMessageHeader(consumedMsg);

                if(MessageType.REQUEST.name().equals(header)){
                    requestMsgs.put(consumedMsg);
                }
                else if(MessageType.REPLY.name().equals(header)){
                    replyMsgs.put(consumedMsg);
                }
                else if(MessageType.PUSH.name().equals(header)){
                    // In pushpull, PUSH messages can come with REQUEST
                    // They are processed directly as updates
                    replyMsgs.put(consumedMsg);
                }

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
