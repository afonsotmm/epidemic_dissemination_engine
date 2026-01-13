package epidemic_core.node.mode.push.components;

import epidemic_core.message.common.MessageDispatcher;
import epidemic_core.message.node_to_node.spread.SpreadMsg;
import epidemic_core.node.mode.push.PushNode;
import epidemic_core.node.mode.push.fsm.push_fsm.logic.PushFsm;
import epidemic_core.node.mode.push.fsm.push_fsm.logic.output.PushFsmResult;
import epidemic_core.node.mode.push.fsm.update_fsm.logic.UpdateFsm;
import epidemic_core.node.mode.push.fsm.update_fsm.logic.output.UpdateFsmResult;
import general.communication.utils.Address;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;

public class Worker {

    private PushNode node;

    private BlockingQueue<String> pushMsgs;
    private List<String> newPushMsgs;

    private BlockingQueue<String> startRoundMsgs;

    private PushFsm pushFsm;
    private UpdateFsm updateFsm;

    private volatile boolean startSignal;

    private final Random rand = new Random();

    public Worker(PushNode node, BlockingQueue<String> pushMsgs, BlockingQueue<String> startRoundMsgs) {
        this.node = node;

        this.pushMsgs = pushMsgs;
        this.newPushMsgs = new ArrayList<>();

        this.startRoundMsgs = startRoundMsgs;

        this.pushFsm = new PushFsm();
        this.updateFsm = new UpdateFsm();

        this.startSignal = false;
    }

    public void workingStep() {
        checkForStartSignal();
        pushFsmHandle();
        updateFsmHandle();
        // Print node state to track message evolution
        node.printNodeState();
    }

    public void workingLoop() {
        while(true) {
            workingStep();

            try {
                Thread.sleep((long) PushNode.RUNNING_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

    }

    // ======================================================= //
    //                  START SIGNAL HANDLE                    //
    // ======================================================= //
    // return true if startRoundMsgs isn't empty
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

            // Send all stored messages to the random neighbour
            if (randNeighAdd != null) {
                for (SpreadMsg message : storedMessages) {
                    try {
                        String stringMsg = message.encode();
                        node.getCommunication().sendMessage(randNeighAdd, stringMsg);
                    } catch (java.io.IOException e) {
                        System.err.println("Error encoding SpreadMsg: " + e.getMessage());
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
                            Boolean gotStored = node.storeOrIgnoreMessage(spreadMsg);
                            if(!gotStored) {
                                System.out.println("[Node " + node.getId() + "] Ignored message - subject '" + spreadMsg.getId().topic().subject() + "' (older timestamp)");
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

