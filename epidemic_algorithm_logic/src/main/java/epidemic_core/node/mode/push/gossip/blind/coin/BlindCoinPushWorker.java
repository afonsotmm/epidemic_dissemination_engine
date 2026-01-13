package epidemic_core.node.mode.push.gossip.blind.coin;

import epidemic_core.message.common.MessageDispatcher;
import epidemic_core.message.common.MessageId;
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


// Worker for Blind Coin Push protocol.
// After pushing a message, tosses a coin with probability 1/k.
// If successful, the node stops spreading that message.

public class BlindCoinPushWorker implements WorkerInterface {

    private GossipPushNode node;

    private BlockingQueue<String> pushMsgs;
    private List<String> newPushMsgs;

    private BlockingQueue<String> startRoundMsgs;

    private PushFsm pushFsm;
    private UpdateFsm updateFsm;

    private volatile boolean startSignal;

    private final Random rand = new Random();
    private final double k; // Probability parameter: 1/k chance to stop spreading

    public BlindCoinPushWorker(GossipPushNode node, BlockingQueue<String> pushMsgs, BlockingQueue<String> startRoundMsgs, double k) {
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
                    
                    // Check if this specific message (MessageId = topic + timestamp) has been removed (Blind Coin)
                    if (node.isMessageRemoved(messageId)) {
                        continue; // Skip this message, don't spread it
                    }
                    
                    String stringMsg = message.encode();
                    node.getCommunication().sendMessage(randNeighAdd, stringMsg);
                    
                    // After pushing, toss coin with probability 1/k
                    // If successful (coin == true), remove this specific message
                    if (GossipNode.tossCoin(k)) {
                        node.removeMessage(messageId);
                        if (node.isRunning()) {
                            System.out.println("[Node " + node.getId() + "] Blind Coin: Removed message '" + 
                                    messageId.topic().subject() + "' from source " + messageId.topic().sourceId() + 
                                    " (timestamp=" + messageId.timestamp() + ", k=" + k + ")");
                        }
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
                            if(!gotStored) {
                                if (node.isRunning()) {
                                    System.out.println("[Node " + node.getId() + "] Ignored message - subject '" + spreadMsg.getId().topic().subject() + "' (older timestamp)");
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[Node " + node.getId() + "] Error decoding/processing spread message: " + e.getMessage());
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

