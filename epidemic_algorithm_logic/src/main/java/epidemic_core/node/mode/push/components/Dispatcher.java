package epidemic_core.node.mode.push.components;

import epidemic_core.message.common.MessageDispatcher;

import java.util.concurrent.BlockingQueue;

public class Dispatcher {

    private BlockingQueue<String> receivedMsgsQueue;
    private BlockingQueue<String> pushMsgs;
    private BlockingQueue<String> startRoundMsgs;
    private volatile boolean running;

    public Dispatcher(BlockingQueue<String> receivedMsgsQueue, BlockingQueue<String> pushMsgs, BlockingQueue<String> startRoundMsgs) {
        this.receivedMsgsQueue = receivedMsgsQueue;
        this.pushMsgs = pushMsgs;
        this.startRoundMsgs = startRoundMsgs;

        running = true;
    }

    public void dispatchingLoop(){

        while(running){
            try {
                String consumedMsg = receivedMsgsQueue.take();
                
                // Only process node_to_node;spread messages
                if (MessageDispatcher.isSpread(consumedMsg)) {
                    pushMsgs.put(consumedMsg);
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
