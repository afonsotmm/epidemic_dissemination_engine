package epidemic_core.node.mode.push.gossip.feedback.coin;

import epidemic_core.message.common.MessageDispatcher;
import epidemic_core.message.common.MessageId;
import epidemic_core.message.node_to_node.feedback.FeedbackMsg;
import epidemic_core.message.node_to_node.spread.SpreadMsg;
import epidemic_core.node.GossipNode;
import epidemic_core.node.mode.push.general.components.WorkerInterface;
import epidemic_core.node.mode.push.general.fsm.push_fsm.logic.PushFsm;
import epidemic_core.node.mode.push.general.fsm.push_fsm.logic.output.PushFsmResult;
import epidemic_core.node.mode.push.general.fsm.update_fsm.logic.UpdateFsm;
import epidemic_core.node.mode.push.general.fsm.update_fsm.logic.output.UpdateFsmResult;
import epidemic_core.node.mode.push.gossip.GossipPushNode;
import general.communication.utils.Address;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;

public class FeedbackCoinPushWorker implements WorkerInterface {

    private GossipPushNode node;

    private BlockingQueue<String> pushMsgs;
    private List<String> newPushMsgs;

    private BlockingQueue<String> startRoundMsgs;

    private PushFsm pushFsm;
    private UpdateFsm updateFsm;

    private volatile boolean startSignal;

    private final Random rand = new Random();
    private final double k;

    public FeedbackCoinPushWorker(GossipPushNode node, BlockingQueue<String> pushMsgs, BlockingQueue<String> startRoundMsgs, double k) {
        this.node = node;
        this.pushMsgs = pushMsgs;
        this.newPushMsgs = new ArrayList<>();
        this.startRoundMsgs = startRoundMsgs;
        this.pushFsm = new PushFsm();
        this.updateFsm = new UpdateFsm();
        this.startSignal = false;
        this.k = k;
    }

    public void workingStep() {
        checkForStartSignal();
        pushFsmHandle();
        updateFsmHandle();
    }

    public void workingLoop() {
        while(node.isRunning()) {
            workingStep();

            try {
                Thread.sleep((long) GossipPushNode.RUNNING_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // ======================================================= //
    //                  START SIGNAL HANDLE                    //
    // ======================================================= //
    public void checkForStartSignal() {
        startSignal = (startRoundMsgs.poll() != null);
    }

    // ======================================================= //
    //                  PUSH FSM HANDLE                        //
    // ======================================================= //
    public void sendPushMsg() {
        List<SpreadMsg> storedMessages = node.getAllStoredMessages();
        List<Integer> neighbours = node.getNeighbours();

        if (neighbours != null && !neighbours.isEmpty() && !storedMessages.isEmpty()) {

            int randNeighIndex = rand.nextInt(neighbours.size());
            Integer randNeighId = neighbours.get(randNeighIndex);
            Address randNeighAdd = node.getNeighbourAddress(randNeighId);

            if (randNeighAdd != null) {
                for (SpreadMsg message : storedMessages) {
                    MessageId messageId = message.getId();

                    if (node.isMessageRemoved(messageId)) {
                        continue;
                    }

                    SpreadMsg forwardMsg = new SpreadMsg(
                        epidemic_core.message.common.Direction.node_to_node.toString(),
                        epidemic_core.message.node_to_node.NodeToNodeMessageType.spread.toString(),
                        messageId.topic().subject(),
                        messageId.topic().sourceId(),
                        messageId.timestamp(),
                        node.getId(),
                        message.getData()
                    );
                    
                    try {
                        String stringMsg = forwardMsg.encode();
                        node.getCommunication().sendMessage(randNeighAdd, stringMsg);
                    } catch (java.io.IOException e) {
                        System.err.println("[Node " + node.getId() + "] Error encoding SpreadMsg: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            } else {
                System.err.println("Warning: Neighbour " + randNeighId + " address not found");
            }
        }
    }

    public void pushFsmHandle() {
        pushFsm.setStartSignal(startSignal);
        startSignal = false;

        PushFsmResult result = pushFsm.step();

        if(result.sendPush) {
            sendPushMsg();
        }

        if(result.updateStatus) {
            for(String newMsgStr: newPushMsgs) {
                try {
                    Object decodedMsg = MessageDispatcher.decode(newMsgStr);
                    if (decodedMsg instanceof SpreadMsg) {
                        SpreadMsg spreadMsg = (SpreadMsg) decodedMsg;
                        if(node.subscriptionCheck(spreadMsg.getId().topic())) {
                            MessageId msgId = spreadMsg.getId();

                            if (node.isMessageRemoved(msgId)) {
                                epidemic_core.node.msg_related.StatusForMessage storedStatus = 
                                    node.getMessagebySubjectAndSource(msgId.topic().subject(), msgId.topic().sourceId());
                                if (storedStatus != null) {
                                    long storedTimestamp = storedStatus.getMessage().getId().timestamp();
                                    if (storedTimestamp >= msgId.timestamp()) {
                                        int originId = spreadMsg.getOriginId();
                                        Address originAddress = node.getNeighbourAddress(originId);
                                        if (originAddress != null) {
                                            FeedbackMsg feedbackMsg = new FeedbackMsg(msgId);
                                            try {
                                                String feedbackString = feedbackMsg.encode();
                                                node.getCommunication().sendMessage(originAddress, feedbackString);
                                                if (node.isRunning()) {
                                                    System.out.println("[Node " + node.getId() + "] Feedback Coin: Sent feedback for removed message '" + 
                                                            msgId.topic().subject() + "' from source " + msgId.topic().sourceId() + 
                                                            " (timestamp=" + msgId.timestamp() + ") to node " + originId);
                                                }
                                            } catch (java.io.IOException e) {
                                                System.err.println("[Node " + node.getId() + "] Error encoding FeedbackMsg: " + e.getMessage());
                                                e.printStackTrace();
                                            }
                                        }
                                    }
                                }
                                continue;
                            }

                            Boolean gotStored = node.storeOrIgnoreMessage(spreadMsg);
                            if (!gotStored) {
                                epidemic_core.node.msg_related.StatusForMessage storedStatus = 
                                    node.getMessagebySubjectAndSource(msgId.topic().subject(), msgId.topic().sourceId());

                                if (storedStatus != null) {
                                    long storedTimestamp = storedStatus.getMessage().getId().timestamp();

                                    if (storedTimestamp >= msgId.timestamp()) {
                                        int originId = spreadMsg.getOriginId();
                                        Address originAddress = node.getNeighbourAddress(originId);

                                        if (originAddress != null) {
                                            FeedbackMsg feedbackMsg = new FeedbackMsg(msgId);
                                            try {
                                                String feedbackString = feedbackMsg.encode();
                                                node.getCommunication().sendMessage(originAddress, feedbackString);
                                                if (node.isRunning()) {
                                                    System.out.println("[Node " + node.getId() + "] Feedback Coin: Sent feedback for message '" + 
                                                            msgId.topic().subject() + "' from source " + msgId.topic().sourceId() + 
                                                            " (timestamp=" + msgId.timestamp() + ") to node " + originId);
                                                }
                                            } catch (java.io.IOException e) {
                                                System.err.println("[Node " + node.getId() + "] Error encoding FeedbackMsg: " + e.getMessage());
                                                e.printStackTrace();
                                            }
                                        }
                                    } else if (node.isRunning()) {
                                        System.out.println("[Node " + node.getId() + "] Ignored message - subject '" + spreadMsg.getId().topic().subject() + "' (older timestamp)");
                                    }
                                }
                            }
                        }
                    } else if (decodedMsg instanceof FeedbackMsg) {
                        FeedbackMsg feedbackMsg = (FeedbackMsg) decodedMsg;
                        MessageId msgId = feedbackMsg.getId();

                        if (!node.isMessageRemoved(msgId) && node.hasMessage(msgId.topic().subject(), msgId.topic().sourceId())) {
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
                    System.err.println("[Node " + node.getId() + "] Error decoding/processing message: " + e.getMessage());
                }
            }

            newPushMsgs.clear();
        }
    }

    // ======================================================= //
    //                  UPDATE FSM HANDLE                      //
    // ======================================================= //
    public void updateFsmHandle() {
        UpdateFsmResult result = updateFsm.step();

        if(result.checkPushMsgs) {
            if (!pushMsgs.isEmpty()) { updateFsm.setFoundPushMsg(true); }
        }

        if(result.saveMsgs) {
            newPushMsgs.clear();
            String newMsg;
            while((newMsg = pushMsgs.poll()) != null) { newPushMsgs.add(newMsg); }
        }
    }
}

