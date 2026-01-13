package epidemic_core.node.mode.pushpull.gossip.blind.coin;

import epidemic_core.message.common.MessageDispatcher;
import epidemic_core.message.common.MessageId;
import epidemic_core.message.common.MessageTopic;
import epidemic_core.message.node_to_node.initial_request.InitialRequestMsg;
import epidemic_core.message.node_to_node.request.RequestMsg;
import epidemic_core.message.node_to_node.request_and_spread.RequestAndSpreadMsg;
import epidemic_core.message.node_to_node.spread.SpreadMsg;
import epidemic_core.node.GossipNode;
import epidemic_core.node.mode.pushpull.gossip.GossipPushPullNode;
import epidemic_core.node.mode.pushpull.general.fsm.pushpull_fsm.logic.PushPullFsm;
import epidemic_core.node.mode.pushpull.general.fsm.pushpull_fsm.logic.output.PushPullFsmResult;
import epidemic_core.node.mode.pushpull.general.fsm.reply_update_fsm.logic.ReplyUpdateFsm;
import epidemic_core.node.mode.pushpull.general.fsm.reply_update_fsm.logic.output.ReplyUpdateFsmResult;
import epidemic_core.node.msg_related.StatusForMessage;
import general.communication.utils.Address;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;

// Worker for Blind Coin PushPull protocol.
// After sending a pushpull request, tosses a coin with probability 1/k.
// If successful, the node stops making requests and spreading that message.
// If the message changes (new timestamp), it can propagate normally.
public class BlindCoinPushPullWorker implements epidemic_core.node.mode.pushpull.general.components.WorkerInterface {

    private BlindCoinPushPullNode node;

    private BlockingQueue<String> replyMsgs;
    private List<String> newReplyMsgs;

    private BlockingQueue<String> requestMsgs;
    private List<String> newReqMsgs;

    private BlockingQueue<String> startRoundMsgs;

    private PushPullFsm pushPullFsm;
    private ReplyUpdateFsm replyUpdateFsm;

    private volatile boolean startSignal;

    private final Random rand = new Random();
    private final double k; // Probability parameter: 1/k chance to stop spreading

    public BlindCoinPushPullWorker(BlindCoinPushPullNode node, BlockingQueue<String> replyMsgs, BlockingQueue<String> requestMsgs, BlockingQueue<String> startRoundMsgs, double k) {
        this.node = node;
        this.replyMsgs = replyMsgs;
        this.newReplyMsgs = new ArrayList<>();
        this.requestMsgs = requestMsgs;
        this.newReqMsgs = new ArrayList<>();
        this.startRoundMsgs = startRoundMsgs;
        this.pushPullFsm = new PushPullFsm();
        this.replyUpdateFsm = new ReplyUpdateFsm();
        this.startSignal = false;
        this.k = k;
    }

    public void workingStep() {
        checkForStartSignal();
        pushPullFsmHandle();
        replyUpdateFsmHandle();
        // Print node state to track message evolution
        node.printNodeState();
    }

    public void workingLoop() {
        while(node.isRunning()) {
            workingStep();

            try {
                Thread.sleep((long) GossipPushPullNode.RUNNING_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void setStartSignal(boolean startSignal) { this.startSignal = startSignal; }

    // ======================================================= //
    //                  START SIGNAL HANDLE                     //
    // ======================================================= //
    public void checkForStartSignal() {
        startSignal = (startRoundMsgs.poll() != null);
    }

    // ======================================================= //
    //                  PUSHPULL FSM HANDLE                    //
    // ======================================================= //
    public void sendPushPullRequest() {
        // Get subscribed topics (interests)
        List<MessageTopic> subscribedTopics = node.getSubscribedTopics();

        // Get a random neighbour and its address
        List<Integer> neighbours = node.getNeighbours();
        if (neighbours.isEmpty()) {
            System.err.println("[Node " + node.getId() + "] No neighbours to pushpull from");
            return;
        }

        int randIndex = rand.nextInt(neighbours.size());
        Integer randNeighId = neighbours.get(randIndex);
        Address randNeighAdd = node.getNeighbourAddress(randNeighId);

        if (randNeighAdd != null) {
            for(MessageTopic topic: subscribedTopics){
                // Check if we have a message for this specific topic (subject + sourceId)
                StatusForMessage statusForMsg = node.getMessagebyTopic(topic);
                
                // if we have no message with a subscribed topic we send a "InitialRequestMsg"
                if(statusForMsg == null){
                    InitialRequestMsg reqMsg = new InitialRequestMsg(node.getId());
                    String request = reqMsg.encode();
                    node.getCommunication().sendMessage(randNeighAdd, request);
                    // For InitialRequestMsg, we don't have a specific MessageId to remove, so no coin toss
                } else {
                    // We have a message for this topic, send RequestAndSpreadMsg with its MessageId
                    SpreadMsg storedMsg = statusForMsg.getMessage();
                    MessageId msgId = storedMsg.getId();
                    
                    // Check if this specific message (MessageId = topic + timestamp) has been removed (Blind Coin)
                    if (node.isMessageRemoved(msgId)) {
                        // If removed, send InitialRequestMsg instead (act as if we don't have the message)
                        // This allows the node to still receive updates if the message changes
                        InitialRequestMsg reqMsg = new InitialRequestMsg(node.getId());
                        String request = reqMsg.encode();
                        node.getCommunication().sendMessage(randNeighAdd, request);
                        continue;
                    }
                    
                    RequestAndSpreadMsg requestAndSpreadMsg = new RequestAndSpreadMsg(
                        msgId,
                        node.getId(),
                        storedMsg.getData()
                    );
                    
                    String requestAndSpreadString = requestAndSpreadMsg.encode();
                    node.getCommunication().sendMessage(randNeighAdd, requestAndSpreadString);
                    
                    // After sending request+spread, toss coin with probability 1/k
                    // If successful (coin == true), remove this specific message
                    if (GossipNode.tossCoin(k)) {
                        node.removeMessage(msgId);
                        if (node.isRunning()) {
                            System.out.println("[Node " + node.getId() + "] Blind Coin: Removed message '" + 
                                    msgId.topic().subject() + "' from source " + msgId.topic().sourceId() + 
                                    " (timestamp=" + msgId.timestamp() + ", k=" + k + ")");
                        }
                    }
                }
            }
        } else {
            System.err.println("Warning: Neighbour " + randNeighId + " address not found");
        }
    }

    public void pushPullFsmHandle() {
        pushPullFsm.setStartSignal(startSignal);
        startSignal = false;

        PushPullFsmResult result = pushPullFsm.step();

        if(result.checkReplyMsgs) {
            if (!replyMsgs.isEmpty()) { pushPullFsm.setFoundReplyMsg(true); }
        }

        if(result.saveReplyMsgs) {
            newReplyMsgs.clear();
            String newMsg;
            while((newMsg = replyMsgs.poll()) != null) { newReplyMsgs.add(newMsg); }
        }

        if(result.updateStatus) {
            for(String newMsgStr: newReplyMsgs) {
                try {
                    Object decodedMsg = MessageDispatcher.decode(newMsgStr);
                    if (decodedMsg instanceof SpreadMsg) {
                        SpreadMsg spreadMsg = (SpreadMsg) decodedMsg;
                        if(node.subscriptionCheck(spreadMsg.getId().topic())) {
                            // Check if this message was previously removed (Blind Coin)
                            MessageId msgId = spreadMsg.getId();
                            if (node.isMessageRemoved(msgId)) {
                                // Ignore this message - it was previously removed
                                if (node.isRunning()) {
                                    System.out.println("[Node " + node.getId() + "] Ignored removed message - subject '" + 
                                            msgId.topic().subject() + "' from source " + msgId.topic().sourceId() + 
                                            " (timestamp=" + msgId.timestamp() + ")");
                                }
                                continue;
                            }
                            
                            Boolean gotStored = node.storeOrIgnoreMessage(spreadMsg);
                            if(!gotStored && node.isRunning()) {
                                System.out.println("[Node " + node.getId() + "] Ignored message - subject '" + spreadMsg.getId().topic().subject() + "' (older timestamp)");
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[Node " + node.getId() + "] Error decoding/processing reply/push message: " + e.getMessage());
                }
            }

            newReplyMsgs.clear();
        }

        if(result.pushPullReq) {
            sendPushPullRequest();
        }
    }

    // ======================================================= //
    //                  REPLY UPDATE FSM HANDLE                //
    // ======================================================= //

    public void sendPushPullReply(String reqMsgStr) {
        try {
            Object decodedMsg = MessageDispatcher.decode(reqMsgStr);
            
            // Can receive RequestMsg, RequestAndSpreadMsg, or InitialRequestMsg
            MessageId reqMsgId = null;
            Integer neighId = null;
            
            if (decodedMsg instanceof InitialRequestMsg) {
                // InitialRequestMsg: send ALL stored messages (no specific MessageId)
                InitialRequestMsg initialRequestMsg = (InitialRequestMsg) decodedMsg;
                neighId = initialRequestMsg.getOriginId();
                
                Address neighAddress = node.getNeighbourAddress(neighId);
                if (neighAddress != null) {
                    // For InitialRequestMsg, send ALL stored messages
                    // But only send messages that are NOT removed
                    List<SpreadMsg> storedMessages = node.getAllStoredMessages();
                    for (SpreadMsg message : storedMessages) {
                        MessageId msgId = message.getId();
                        // Only send if message is not removed
                        if (!node.isMessageRemoved(msgId)) {
                            String stringMsg = message.encode();
                            node.getCommunication().sendMessage(neighAddress, stringMsg);
                        }
                    }
                } else {
                    System.err.println("Warning: Neighbour " + neighId + " address not found");
                }
                return; // Early return for InitialRequestMsg
            } else if (decodedMsg instanceof RequestMsg) {
                RequestMsg requestMsg = (RequestMsg) decodedMsg;
                reqMsgId = requestMsg.getId();
                neighId = requestMsg.getOriginId();
            } else if (decodedMsg instanceof RequestAndSpreadMsg) {
                RequestAndSpreadMsg requestAndSpreadMsg = (RequestAndSpreadMsg) decodedMsg;
                reqMsgId = requestAndSpreadMsg.getId();
                neighId = requestAndSpreadMsg.getOriginId();
                // Note: The SPREAD part is already processed by the Dispatcher and FSM
            }
            
            if (neighId != null && reqMsgId != null) {
                Address neighAddress = node.getNeighbourAddress(neighId);

                if (neighAddress != null) {
                    String reqSubject = reqMsgId.topic().subject();
                    int reqSourceId = reqMsgId.topic().sourceId();
                    long reqTimestamp = reqMsgId.timestamp();

                    // Check if we have this message (subject + sourceId)
                    if (node.hasMessage(reqSubject, reqSourceId)) {
                        // Get the stored message
                        StatusForMessage statusForMsg = node.getMessagebySubjectAndSource(reqSubject, reqSourceId);
                        SpreadMsg storedMessage = statusForMsg.getMessage();
                        MessageId storedMsgId = storedMessage.getId();
                        long storedTimestamp = storedMsgId.timestamp();

                        // Reply only if we have a more recent version AND it's not removed
                        if (storedTimestamp > reqTimestamp && !node.isMessageRemoved(storedMsgId)) {
                            String stringMsg = storedMessage.encode();
                            node.getCommunication().sendMessage(neighAddress, stringMsg);
                        }
                    }
                } else {
                    System.err.println("Warning: Neighbour " + neighId + " address not found");
                }
            }
        } catch (Exception e) {
            System.err.println("[Node " + node.getId() + "] Error processing pushpull reply: " + e.getMessage());
        }
    }

    public void replyUpdateFsmHandle() {
        ReplyUpdateFsmResult result = replyUpdateFsm.step();

        if(result.checkReqMsgs) {
            if (!requestMsgs.isEmpty()) { replyUpdateFsm.setFoundReqMsg(true); }
        }

        if(result.sendReply) {
            newReqMsgs.clear();
            String newMsg;
            while((newMsg = requestMsgs.poll()) != null) { newReqMsgs.add(newMsg); }

            // Replying every request received
            for(String newReqMsgStr: newReqMsgs) {
                sendPushPullReply(newReqMsgStr);
            }
            
            newReqMsgs.clear();
        }
    }
}

