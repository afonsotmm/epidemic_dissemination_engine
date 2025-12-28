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

// Worker for Feedback Coin Push protocol.
// When receiving a spread message that is already known, sends FeedbackMsg.
// When receiving FeedbackMsg, tosses a coin with probability 1/k.
// If successful, the node stops spreading that message.
public class FeedbackCoinPushWorker implements WorkerInterface {

    private GossipPushNode node;

    private BlockingQueue<String> pushMsgs;
    private List<String> newPushMsgs;

    private BlockingQueue<String> startRoundMsgs;

    private PushFsm pushFsm;
    private UpdateFsm updateFsm;

    private volatile boolean startSignal;

    private final Random rand = new Random();
    private final double k; // Probability parameter: 1/k chance to stop spreading

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
        // Print node state to track message evolution
        node.printNodeState();
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

        // Only send if there are neighbours and stored messages
        if (neighbours != null && !neighbours.isEmpty() && !storedMessages.isEmpty()) {
            // Pick a random neighbour
            int randNeighIndex = rand.nextInt(neighbours.size());
            Integer randNeighId = neighbours.get(randNeighIndex);
            Address randNeighAdd = node.getNeighbourAddress(randNeighId);

            // Send all stored messages to the random neighbour (if not removed)
            if (randNeighAdd != null) {
                for (SpreadMsg message : storedMessages) {
                    MessageId messageId = message.getId();
                    
                    // Check if this specific message (MessageId = topic + timestamp) has been removed
                    if (node.isMessageRemoved(messageId)) {
                        continue; // Skip this message, don't spread it
                    }
                    
                    String stringMsg = message.encode();
                    node.getCommunication().sendMessage(randNeighAdd, stringMsg);
                    // Note: No coin toss here only when receiving FeedbackMsg
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
                            
                            // Try to store the message
                            Boolean gotStored = node.storeOrIgnoreMessage(spreadMsg);
                            if (!gotStored) {
                                // Message was not stored - check if we already have it (same or more recent) to send FeedbackMsg
                                epidemic_core.node.msg_related.StatusForMessage storedStatus = 
                                    node.getMessagebySubjectAndSource(msgId.topic().subject(), msgId.topic().sourceId());
                                if (storedStatus != null) {
                                    long storedTimestamp = storedStatus.getMessage().getId().timestamp();
                                    // If we have same or more recent version, send FeedbackMsg
                                    if (storedTimestamp >= msgId.timestamp()) {
                                        // Send FeedbackMsg to the originator
                                        int originId = spreadMsg.getOriginId();
                                        Address originAddress = node.getNeighbourAddress(originId);
                                        if (originAddress != null) {
                                            FeedbackMsg feedbackMsg = new FeedbackMsg(msgId);
                                            String feedbackString = feedbackMsg.encode();
                                            node.getCommunication().sendMessage(originAddress, feedbackString);
                                            if (node.isRunning()) {
                                                System.out.println("[Node " + node.getId() + "] Feedback Coin: Sent feedback for message '" + 
                                                        msgId.topic().subject() + "' from source " + msgId.topic().sourceId() + 
                                                        " (timestamp=" + msgId.timestamp() + ") to node " + originId);
                                            }
                                        }
                                    } else if (node.isRunning()) {
                                        // Message is older (we have a more recent version)
                                        System.out.println("[Node " + node.getId() + "] Ignored message - subject '" + spreadMsg.getId().topic().subject() + "' (older timestamp)");
                                    }
                                }
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

