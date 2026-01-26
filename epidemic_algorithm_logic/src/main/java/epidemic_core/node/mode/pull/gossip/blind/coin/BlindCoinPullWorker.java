package epidemic_core.node.mode.pull.gossip.blind.coin;

import epidemic_core.message.common.Direction;
import epidemic_core.message.common.MessageDispatcher;
import epidemic_core.message.common.MessageId;
import epidemic_core.message.common.MessageTopic;
import epidemic_core.message.node_to_node.NodeToNodeMessageType;
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

public class BlindCoinPullWorker implements epidemic_core.node.mode.pull.general.components.WorkerInterface {

    private BlindCoinPullNode node;

    private BlockingQueue<String> replyMsgs;
    private List<String> newReplyMsgs;

    private BlockingQueue<String> requestMsgs;
    private List<String> newReqMsgs;

    private BlockingQueue<String> startRoundMsgs;

    private PullFsm pullFsm;
    private ReplyFsm replyFsm;

    private volatile boolean startSignal;

    private final Random rand = new Random();
    private final double k;

    public BlindCoinPullWorker(BlindCoinPullNode node, BlockingQueue<String> replyMsgs, BlockingQueue<String> requestMsgs, BlockingQueue<String> startRoundMsgs, double k) {
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
        List<MessageTopic> subscribedTopics = node.getSubscribedTopics();

        List<Integer> neighbours = node.getNeighbours();
        if (neighbours.isEmpty()) {
            System.err.println("[Node " + node.getId() + "] No neighbours to pull from");
            return;
        }
        int randIndex = rand.nextInt(neighbours.size());
        Integer randNeighId = neighbours.get(randIndex);
        Address randNeighAdd = node.getNeighbourAddress(randNeighId);

        for(MessageTopic topic: subscribedTopics){
            StatusForMessage statusForMsg = node.getMessagebyTopic(topic);

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
                MessageId messageId = storedMsg.getId();

                if (node.isMessageRemoved(messageId)) {
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

                if (GossipNode.tossCoin(k)) {
                    node.removeMessage(messageId);
                    if (node.isRunning()) {
                        System.out.println("[Node " + node.getId() + "] Blind Coin: Removed message '" + 
                                messageId.topic().subject() + "' from source " + messageId.topic().sourceId() + 
                                " (timestamp=" + messageId.timestamp() + ", k=" + k + ")");
                    }
                }
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
                            MessageId msgId = spreadMsg.getId();
                            if (node.isMessageRemoved(msgId)) {
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
                                System.err.println("[Node " + node.getId() + "] Error encoding SpreadMsg: " + e.getMessage());
                                e.printStackTrace();
                            }
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
                    List<SpreadMsg> storedMessages = node.getAllStoredMessages();
                    for (SpreadMsg message : storedMessages) {
                        MessageId msgId = message.getId();
                        if (!node.isMessageRemoved(msgId)) {
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

            for(String newReqMsgStr: newReqMsgs) {
                sendPullReply(newReqMsgStr);
            }
        }
    }
}

