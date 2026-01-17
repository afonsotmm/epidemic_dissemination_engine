package epidemic_core.node.mode.pull.gossip.feedback.coin;

import epidemic_core.message.common.Direction;
import epidemic_core.message.common.MessageDispatcher;
import epidemic_core.message.common.MessageId;
import epidemic_core.message.common.MessageTopic;
import epidemic_core.message.node_to_node.NodeToNodeMessageType;
import epidemic_core.message.node_to_node.feedback.FeedbackMsg;
import epidemic_core.message.node_to_node.initial_request.InitialRequestMsg;
import epidemic_core.message.node_to_node.request.RequestMsg;
import epidemic_core.message.node_to_node.spread.SpreadMsg;
import epidemic_core.node.GossipNode;
import epidemic_core.node.mode.pull.gossip.GossipPullNode;
import epidemic_core.node.mode.pull.general.fsm.pull_fsm.logic.PullFsm;
import epidemic_core.node.mode.pull.general.fsm.pull_fsm.logic.output.PullFsmResult;
import epidemic_core.node.mode.pull.general.fsm.reply_fsm.logic.ReplyFsm;
import epidemic_core.node.mode.pull.general.fsm.reply_fsm.logic.output.ReplyFsmResult;
import epidemic_core.node.msg_related.StatusForMessage;
import general.communication.utils.Address;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;

// Worker for Feedback Coin Pull protocol.
// When receiving a request with MessageId that is already known, sends FeedbackMsg.
// When receiving FeedbackMsg, tosses a coin with probability 1/k.
// If successful, the node stops making requests and spreading that message.
public class FeedbackCoinPullWorker implements epidemic_core.node.mode.pull.general.components.WorkerInterface {

    private FeedbackCoinPullNode node;

    private BlockingQueue<String> replyMsgs;
    private List<String> newReplyMsgs;

    private BlockingQueue<String> requestMsgs;
    private List<String> newReqMsgs;

    private BlockingQueue<String> startRoundMsgs;

    private PullFsm pullFsm;
    private ReplyFsm replyFsm;

    private volatile boolean startSignal;

    private final Random rand = new Random();
    private final double k; // Probability parameter: 1/k chance to stop spreading

    public FeedbackCoinPullWorker(FeedbackCoinPullNode node, BlockingQueue<String> replyMsgs, BlockingQueue<String> requestMsgs, BlockingQueue<String> startRoundMsgs, double k) {
        this.node = node;
        this.replyMsgs = replyMsgs;
        this.newReplyMsgs = new ArrayList<>();
        this.requestMsgs = requestMsgs;
        this.newReqMsgs = new ArrayList<>();
        this.startRoundMsgs = startRoundMsgs;
        this.pullFsm = new PullFsm();
        this.replyFsm = new ReplyFsm();
        this.startSignal = false;
        this.k = k;
    }

    public void workingStep() {
        checkForStartSignal();
        pullFsmHandle();
        replyFsmHandle();
        // Print node state removed - too verbose
        // node.printNodeState();
    }

    public void workingLoop() {
        while(node.isRunning()) {
            workingStep();

            try {
                Thread.sleep((long) GossipPullNode.RUNNING_INTERVAL);
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
    //                  PULL FSM HANDLE                        //
    // ======================================================= //
    public void sendPullRequest() {
        // Get subscribed topics (interests)
        List<MessageTopic> subscribedTopics = node.getSubscribedTopics();

        // Get a random neighbour and its address
        List<Integer> neighbours = node.getNeighbours();
        if (neighbours.isEmpty()) {
            System.err.println("[Node " + node.getId() + "] No neighbours to pull from");
            return;
        }
        int randIndex = rand.nextInt(neighbours.size());
        Integer randNeighId = neighbours.get(randIndex);
        Address randNeighAdd = node.getNeighbourAddress(randNeighId);

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
                // We have a message for this topic, send RequestMsg with its MessageId
                SpreadMsg storedMsg = statusForMsg.getMessage();
                MessageId messageId = storedMsg.getId();
                
                // Check if this specific message (MessageId = topic + timestamp) has been removed
                if (node.isMessageRemoved(messageId)) {
                    // We don't make requests for this topic anymore (while we dont have a newer version)
                    continue;
                }
                
                RequestMsg reqMsg = new RequestMsg(
                    Direction.node_to_node.toString(),
                    NodeToNodeMessageType.request.toString(),
                    messageId.topic().subject(),
                    messageId.topic().sourceId(),
                    messageId.timestamp(),
                    node.getId()
                );
                try {
                    String request = reqMsg.encode();
                    node.getCommunication().sendMessage(randNeighAdd, request);
                } catch (java.io.IOException e) {
                    System.err.println("Error encoding RequestMsg: " + e.getMessage());
                    e.printStackTrace();
                }
                // Note: No coin toss here - only when receiving FeedbackMsg
            }
        }
    }

    public void pullFsmHandle() {
        pullFsm.setStartSignal(startSignal);
        startSignal = false;

        PullFsmResult result = pullFsm.step();

        if(result.checkReplyMsgs) {
            if (!replyMsgs.isEmpty()) { pullFsm.setFoundReplyMsg(true); }
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
                            if (!gotStored && node.isRunning()) {
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
                    System.err.println("[Node " + node.getId() + "] Error decoding/processing reply message: " + e.getMessage());
                }
            }

            newReplyMsgs.clear();
        }

        if(result.pullReq) {
            sendPullRequest();
        }
    }

    // ======================================================= //
    //                  REPLY FSM HANDLE                        //
    // ======================================================= //

    public void sendPullReply(String reqMsgStr) {
        try {
            Object decodedMsg = MessageDispatcher.decode(reqMsgStr);
            if (decodedMsg instanceof RequestMsg) {
                RequestMsg requestMsg = (RequestMsg) decodedMsg;

                String reqSubject = requestMsg.getId().topic().subject();
                int reqSourceId = requestMsg.getId().topic().sourceId();
                long reqTimestamp = requestMsg.getId().timestamp();

                Integer neighId = requestMsg.getOriginId();
                Address neighAddress = node.getNeighbourAddress(neighId);

                if (neighAddress != null) {
                    // Check if we have this message (subject + sourceId)
                    if (node.hasMessage(reqSubject, reqSourceId)) {
                        // Get the stored message
                        StatusForMessage statusForMsg = node.getMessagebySubjectAndSource(reqSubject, reqSourceId);
                        SpreadMsg storedMessage = statusForMsg.getMessage();
                        long storedTimestamp = storedMessage.getId().timestamp();

                        // If we have same or more recent version, send FeedbackMsg instead of SpreadMsg
                        if (storedTimestamp >= reqTimestamp) {
                            // Send FeedbackMsg to indicate we already have this message
                            FeedbackMsg feedbackMsg = new FeedbackMsg(requestMsg.getId());
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
                            // Note: We don't send SpreadMsg if we have same or more recent version
                        } else {
                            // We have an older version - don't send anything (requestor already has newer version)
                            // This matches the behavior of normal Pull protocol
                        }
                    }
                } else {
                    System.err.println("Warning: Neighbour " + neighId + " address not found");
                }
            } else if(decodedMsg instanceof InitialRequestMsg) {
                InitialRequestMsg initialRequestMsg = (InitialRequestMsg) decodedMsg;
                Integer neighId = initialRequestMsg.getOriginId();
                Address neighAddress = node.getNeighbourAddress(neighId);

                if (neighAddress != null) {
                    // For InitialRequestMsg, send ALL stored messages (generic pull request)
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
            }

        } catch (Exception e) {
            System.err.println("[Node " + node.getId() + "] Error processing pull reply: " + e.getMessage());
        }
    }

    public void replyFsmHandle() {
        ReplyFsmResult result = replyFsm.step();

        if(result.checkReqMsgs) {
            if (!requestMsgs.isEmpty()) { replyFsm.setFoundReqMsg(true); }
        }

        if(result.sendReply) {
            newReqMsgs.clear();
            String newMsg;
            while((newMsg = requestMsgs.poll()) != null) { newReqMsgs.add(newMsg); }

            // Replying every request received
            for(String newReqMsgStr: newReqMsgs) {
                sendPullReply(newReqMsgStr);
            }
        }
    }
}

