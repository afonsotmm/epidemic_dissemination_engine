package epidemic_core.node.mode.pushpull.components;

import epidemic_core.message.common.MessageDispatcher;
import epidemic_core.message.node_to_node.request_and_spread.RequestAndSpreadMsg;
import epidemic_core.message.node_to_node.spread.SpreadMsg;

import java.util.concurrent.BlockingQueue;

public class Dispatcher {

    private BlockingQueue<String> receivedMsgsQueue;
    private BlockingQueue<String> replyMsgs;
    private BlockingQueue<String> requestMsgs;
    private BlockingQueue<String> startRoundMsgs;
    private volatile boolean running;

    public Dispatcher(BlockingQueue<String> receivedMsgsQueue, BlockingQueue<String> replyMsgs, BlockingQueue<String> requestMsgs, BlockingQueue<String> startRoundMsgs) {
        this.receivedMsgsQueue = receivedMsgsQueue;
        this.replyMsgs = replyMsgs;
        this.requestMsgs = requestMsgs;
        this.startRoundMsgs = startRoundMsgs;

        running = true;
    }

    public void dispatchingLoop(){

        while(running){
            try {
                String consumedMsg = receivedMsgsQueue.take();
                
                // Only process node_to_node messages
                if (MessageDispatcher.isRequestAndSpread(consumedMsg)) {
                    // Put the full message in requestMsgs for reply processing
                    requestMsgs.put(consumedMsg);
                    
                    // Also extract the SPREAD part and put it in replyMsgs for update processing
                    try {
                        Object decodedMsg = MessageDispatcher.decode(consumedMsg);
                        if (decodedMsg instanceof RequestAndSpreadMsg) {
                            RequestAndSpreadMsg requestAndSpreadMsg = (RequestAndSpreadMsg) decodedMsg;
                            // Create a SpreadMsg from the RequestAndSpreadMsg
                            SpreadMsg spreadPart = new SpreadMsg(
                                requestAndSpreadMsg.getId(),
                                requestAndSpreadMsg.getOriginId(),
                                requestAndSpreadMsg.getData()
                            );
                            String spreadMsgString = spreadPart.encode();
                            replyMsgs.put(spreadMsgString);
                        }
                    } catch (Exception e) {
                        System.err.println("[Dispatcher] Error extracting SPREAD from RequestAndSpreadMsg: " + e.getMessage());
                    }
                } else if (MessageDispatcher.isRequest(consumedMsg) || MessageDispatcher.isInitialRequest(consumedMsg)) {
                    // REQUEST or INITIAL_REQUEST (when node has no messages to send or wants to discover)
                    requestMsgs.put(consumedMsg);
                } else if (MessageDispatcher.isSpread(consumedMsg)) {
                    replyMsgs.put(consumedMsg);
                } else if (MessageDispatcher.isFeedback(consumedMsg)) {
                    replyMsgs.put(consumedMsg);
                } else if (MessageDispatcher.isStartRound(consumedMsg)) {
                    startRoundMsgs.put(consumedMsg);
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
