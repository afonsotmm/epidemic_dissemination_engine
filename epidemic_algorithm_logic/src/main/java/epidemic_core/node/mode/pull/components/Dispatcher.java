package epidemic_core.node.mode.pull.components;

import epidemic_core.message.common.MessageDispatcher;
import java.util.concurrent.BlockingQueue;

public class Dispatcher {

    private BlockingQueue<String> msgsQueue;
    private BlockingQueue<String> replyMsgs;
    private BlockingQueue<String> requestMsgs;
    private BlockingQueue<String> startRoundMsgs;

    private volatile boolean running;

    public Dispatcher(BlockingQueue<String> msgsQueue, BlockingQueue<String> replyMsgs, BlockingQueue<String> requestMsgs, BlockingQueue<String> startRoundMsgs) {
        this.msgsQueue = msgsQueue;
        this.replyMsgs = replyMsgs;
        this.requestMsgs = requestMsgs;
        this.startRoundMsgs = startRoundMsgs;

        running = true;
    }

    public void dispatchingLoop(){

        while(running){
            try {
                String consumedMsg = msgsQueue.take();
                
                if (MessageDispatcher.isRequest(consumedMsg) || MessageDispatcher.isInitialRequest(consumedMsg)) {
                    requestMsgs.put(consumedMsg);
                } else if (MessageDispatcher.isSpread(consumedMsg)) {
                    replyMsgs.put(consumedMsg);
                } else if (MessageDispatcher.isFeedback(consumedMsg)) {
                    replyMsgs.put(consumedMsg);
                } else if (MessageDispatcher.isStartRound(consumedMsg)) {
                    startRoundMsgs.put(consumedMsg);
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
