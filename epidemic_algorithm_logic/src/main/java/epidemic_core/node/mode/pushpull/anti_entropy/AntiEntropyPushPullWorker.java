package epidemic_core.node.mode.pushpull.anti_entropy;

import epidemic_core.message.common.Direction;
import epidemic_core.message.common.MessageId;
import epidemic_core.message.common.MessageDispatcher;
import epidemic_core.message.common.MessageTopic;
import epidemic_core.message.node_to_node.NodeToNodeMessageType;
import epidemic_core.message.node_to_node.initial_request.InitialRequestMsg;
import epidemic_core.message.node_to_node.request.RequestMsg;
import epidemic_core.message.node_to_node.request_and_spread.RequestAndSpreadMsg;
import epidemic_core.message.node_to_node.spread.SpreadMsg;
import epidemic_core.node.mode.pushpull.general.components.WorkerInterface;
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

public class AntiEntropyPushPullWorker implements WorkerInterface {

    private AntiEntropyPushPullNode node;

    private BlockingQueue<String> replyMsgs;
    private List<String> newReplyMsgs;

    private BlockingQueue<String> requestMsgs;
    private List<String> newReqMsgs;

    private BlockingQueue<String> startRoundMsgs;

    private PushPullFsm pushPullFsm;
    private ReplyUpdateFsm replyUpdateFsm;

    private volatile boolean startSignal;

    private final Random rand = new Random();

    public AntiEntropyPushPullWorker(AntiEntropyPushPullNode node, BlockingQueue<String> replyMsgs, BlockingQueue<String> requestMsgs, BlockingQueue<String> startRoundMsgs) {
        this.node = node;

        this.replyMsgs = replyMsgs;
        this.newReplyMsgs = new ArrayList<>();

        this.requestMsgs = requestMsgs;
        this.newReqMsgs = new ArrayList<>();

        this.startRoundMsgs = startRoundMsgs;

        this.pushPullFsm = new PushPullFsm();
        this.replyUpdateFsm = new ReplyUpdateFsm();

        this.startSignal = false;
    }

    public void workingStep() {
        checkForStartSignal();
        pushPullFsmHandle();
        replyUpdateFsmHandle();
    }

    public void workingLoop() {
        while(node.isRunning()) {
            workingStep();

            try {
                Thread.sleep((long) AntiEntropyPushPullNode.RUNNING_INTERVAL);
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
                    SpreadMsg storedMsg = statusForMsg.getMessage();
                    MessageId msgId = storedMsg.getId();
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
                            Boolean gotStored = node.storeOrIgnoreMessage(spreadMsg);
                            if(!gotStored) {
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

            MessageId reqMsgId = null;
            Integer neighId = null;
            
            if (decodedMsg instanceof InitialRequestMsg) {
                InitialRequestMsg initialRequestMsg = (InitialRequestMsg) decodedMsg;
                neighId = initialRequestMsg.getOriginId();
                
                Address neighAddress = node.getNeighbourAddress(neighId);
                if (neighAddress != null) {
                    List<SpreadMsg> storedMessages = node.getAllStoredMessages();
                    for (SpreadMsg message : storedMessages) {
                        MessageId msgId = message.getId();
                        SpreadMsg forwardMsg = new SpreadMsg(
                            epidemic_core.message.common.Direction.node_to_node.toString(),
                            epidemic_core.message.node_to_node.NodeToNodeMessageType.spread.toString(),
                            msgId.topic().subject(),
                            msgId.topic().sourceId(),
                            msgId.timestamp(),
                            node.getId(),
                            message.getData()
                        );
                        
                        try {
                            String stringMsg = forwardMsg.encode();
                            node.getCommunication().sendMessage(neighAddress, stringMsg);
                        } catch (java.io.IOException e) {
                            System.err.println("Error encoding SpreadMsg: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                } else {
                    System.err.println("Warning: Neighbour " + neighId + " address not found");
                }
                return;
            } else if (decodedMsg instanceof RequestMsg) {
                RequestMsg requestMsg = (RequestMsg) decodedMsg;
                reqMsgId = requestMsg.getId();
                neighId = requestMsg.getOriginId();
            } else if (decodedMsg instanceof RequestAndSpreadMsg) {
                RequestAndSpreadMsg requestAndSpreadMsg = (RequestAndSpreadMsg) decodedMsg;
                reqMsgId = requestAndSpreadMsg.getId();
                neighId = requestAndSpreadMsg.getOriginId();
            }
            
            if (neighId != null && reqMsgId != null) {
                Address neighAddress = node.getNeighbourAddress(neighId);

                if (neighAddress != null) {
                    String reqSubject = reqMsgId.topic().subject();
                    int reqSourceId = reqMsgId.topic().sourceId();
                    long reqTimestamp = reqMsgId.timestamp();

                    if (node.hasMessage(reqSubject, reqSourceId)) {
                        StatusForMessage statusForMsg = node.getMessagebySubjectAndSource(reqSubject, reqSourceId);
                        SpreadMsg storedMessage = statusForMsg.getMessage();
                        long storedTimestamp = storedMessage.getId().timestamp();

                        if (storedTimestamp > reqTimestamp) {
                            MessageId storedMsgId = storedMessage.getId();
                            SpreadMsg forwardMsg = new SpreadMsg(
                                epidemic_core.message.common.Direction.node_to_node.toString(),
                                epidemic_core.message.node_to_node.NodeToNodeMessageType.spread.toString(),
                                storedMsgId.topic().subject(),
                                storedMsgId.topic().sourceId(),
                                storedMsgId.timestamp(),
                                node.getId(),
                                storedMessage.getData()
                            );
                            
                            try {
                                String stringMsg = forwardMsg.encode();
                                node.getCommunication().sendMessage(neighAddress, stringMsg);
                            } catch (java.io.IOException e) {
                                System.err.println("Error encoding SpreadMsg: " + e.getMessage());
                                e.printStackTrace();
                            }
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

            for(String newReqMsgStr: newReqMsgs) {
                sendPushPullReply(newReqMsgStr);
            }
            
            newReqMsgs.clear();
        }

    }
}

