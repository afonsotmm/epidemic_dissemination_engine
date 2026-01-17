package epidemic_core.node.mode.pushpull.gossip.feedback.coin;

import epidemic_core.message.common.Direction;
import epidemic_core.message.common.MessageDispatcher;
import epidemic_core.message.common.MessageId;
import epidemic_core.message.common.MessageTopic;
import epidemic_core.message.node_to_node.NodeToNodeMessageType;
import epidemic_core.message.node_to_node.feedback.FeedbackMsg;
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

// Worker for Feedback Coin PushPull protocol.
// When receiving a request with MessageId that is already known, sends FeedbackMsg.
// When receiving FeedbackMsg, tosses a coin with probability 1/k.
// If successful, the node stops making requests and spreading that message.
// If the message changes (new timestamp), it can propagate normally.
public class FeedbackCoinPushPullWorker implements epidemic_core.node.mode.pushpull.general.components.WorkerInterface {

    private FeedbackCoinPushPullNode node;

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

    public FeedbackCoinPushPullWorker(FeedbackCoinPushPullNode node, BlockingQueue<String> replyMsgs, BlockingQueue<String> requestMsgs, BlockingQueue<String> startRoundMsgs, double k) {
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
        // Print node state removed - too verbose
        // node.printNodeState();
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
                    InitialRequestMsg reqMsg = new InitialRequestMsg(
                        Direction.node_to_node.toString(),
                        NodeToNodeMessageType.initial_request.toString(),
                        node.getId()
                    );
                    try {
                        String request = reqMsg.encode();
                        node.getCommunication().sendMessage(randNeighAdd, request);
                    } catch (java.io.IOException e) {
                        System.err.println("Error encoding InitialRequestMsg: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    // We have a message for this topic, send RequestAndSpreadMsg with its MessageId
                    SpreadMsg storedMsg = statusForMsg.getMessage();
                    MessageId msgId = storedMsg.getId();
                    
                    // Check if this specific message (MessageId = topic + timestamp) has been removed
                    if (node.isMessageRemoved(msgId)) {
                        continue;
                    }
                    
                    RequestAndSpreadMsg requestAndSpreadMsg = new RequestAndSpreadMsg(
                        Direction.node_to_node.toString(),
                        NodeToNodeMessageType.request_and_spread.toString(),
                        msgId.topic().subject(),
                        msgId.topic().sourceId(),
                        msgId.timestamp(),
                        node.getId(),
                        storedMsg.getData()
                    );
                    
                    try {
                        String requestAndSpreadString = requestAndSpreadMsg.encode();
                        node.getCommunication().sendMessage(randNeighAdd, requestAndSpreadString);
                    } catch (java.io.IOException e) {
                        System.err.println("Error encoding RequestAndSpreadMsg: " + e.getMessage());
                        e.printStackTrace();
                    }
                    // Note: No coin toss here - only when receiving FeedbackMsg
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
                            // Check if this message was previously removed
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
                    } else if (decodedMsg instanceof FeedbackMsg) {
                        // Handle FeedbackMsg - this is where we do the coin toss
                        FeedbackMsg feedbackMsg = (FeedbackMsg) decodedMsg;
                        MessageId msgId = feedbackMsg.getId();
                        
                        // Check if we still have this message (might have been removed already)
                        if (!node.isMessageRemoved(msgId) && node.hasMessage(msgId.topic().subject(), msgId.topic().sourceId())) {
                            // Toss coin with probability 1/k
                            // If successful (coin == true), remove this specific message
                            if (GossipNode.tossCoin(k)) {
                                node.removeMessage(msgId);
                                if (node.isRunning()) {
                                    System.out.println("[Node " + node.getId() + "] Feedback Coin: Removed message '" + 
                                            msgId.topic().subject() + "' from source " + msgId.topic().sourceId() + 
                                            " (timestamp=" + msgId.timestamp() + ", k=" + k + ") after receiving feedback");
                                }
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
                            try {
                                String stringMsg = message.encode();
                                node.getCommunication().sendMessage(neighAddress, stringMsg);
                            } catch (java.io.IOException e) {
                                System.err.println("[Node " + node.getId() + "] Error encoding SpreadMsg: " + e.getMessage());
                                e.printStackTrace();
                            }
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

                        // Handle different version scenarios
                        if (storedTimestamp == reqTimestamp && !node.isMessageRemoved(storedMsgId)) {
                            // Same version: send FeedbackMsg to indicate we already have this message
                            FeedbackMsg feedbackMsg = new FeedbackMsg(reqMsgId);
                            try {
                                String feedbackString = feedbackMsg.encode();
                                node.getCommunication().sendMessage(neighAddress, feedbackString);
                                if (node.isRunning()) {
                                    System.out.println("[Node " + node.getId() + "] Feedback Coin: Sent feedback for message '" + 
                                            reqSubject + "' from source " + reqSourceId + 
                                            " (timestamp=" + reqTimestamp + ") to node " + neighId);
                                }
                            } catch (java.io.IOException e) {
                                System.err.println("[Node " + node.getId() + "] Error encoding FeedbackMsg: " + e.getMessage());
                                e.printStackTrace();
                            }
                        } else if (storedTimestamp > reqTimestamp && !node.isMessageRemoved(storedMsgId)) {
                            // More recent version: send SpreadMsg to update the requestor
                            try {
                                String stringMsg = storedMessage.encode();
                                node.getCommunication().sendMessage(neighAddress, stringMsg);
                            } catch (java.io.IOException e) {
                                System.err.println("[Node " + node.getId() + "] Error encoding SpreadMsg: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                        // If we have older version, don't send anything (requestor already has newer version)
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

