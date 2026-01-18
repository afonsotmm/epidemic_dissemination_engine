package epidemic_core.node.mode.push.general.components;

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
                
                // Process node_to_node messages (spread and feedback)
                if (MessageDispatcher.isSpread(consumedMsg)) {
                    pushMsgs.put(consumedMsg);
                } else if (MessageDispatcher.isFeedback(consumedMsg)) {
                    pushMsgs.put(consumedMsg);
                } else if (MessageDispatcher.isStartRound(consumedMsg)) {
                    System.out.println("[Dispatcher] Received StartRoundMsg - triggering round");
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
