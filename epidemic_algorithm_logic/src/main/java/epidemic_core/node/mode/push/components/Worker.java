package epidemic_core.node.mode.push.components;

import epidemic_core.message.Message;
import epidemic_core.message.msgTypes.NodeToNode;
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

    private PushFsm pushFsm;
    private UpdateFsm updateFsm;

    private volatile boolean startSignal;

    private final Random rand = new Random();

    public Worker(PushNode node, BlockingQueue<String> pushMsgs) {
        this.node = node;

        this.pushMsgs = pushMsgs;
        this.newPushMsgs = new ArrayList<>();

        this.pushFsm = new PushFsm();
        this.updateFsm = new UpdateFsm();

        this.startSignal = false;
    }

    public void workingStep() {
        pushFsmHandle();
        updateFsmHandle();
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

    public void setStartSignal(boolean startSignal) { this.startSignal = startSignal; }

    // ======================================================= //
    //                  PUSH FSM HANDLE                        //
    // ======================================================= //
    public void sendPushMsg() {
        List<Message> storedMessages = node.getAllStoredMessages();
        List<Integer> neighbours = node.getNeighbours();

        // Only send if there are neighbours and stored messages
        if (neighbours != null && !neighbours.isEmpty() && !storedMessages.isEmpty()) {
            // Send a random message to a random neighbour

            // Pick a random message
            int randMsgIndex = rand.nextInt(storedMessages.size());
            Message message = storedMessages.get(randMsgIndex);
            String stringMsg = message.encodeMessage(NodeToNode.PUSH);

            // Pick a random neighbour
            int randNeighIndex = rand.nextInt(neighbours.size());
            Integer randNeighId = neighbours.get(randNeighIndex);
            Address randNeighAdd = node.getNeighbourAddress(randNeighId);

            // Send Message only if address is valid
            if (randNeighAdd != null) {
                node.getCommunication().sendMessage(randNeighAdd, stringMsg);
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
                    Message newMsg = Message.decodeMessage(newMsgStr);
                    Boolean gotStored = node.storeOrIgnoreMessage(newMsg);
                    if(!gotStored) {
                        System.out.println("[Node " + node.getId() + "] Ignored message - subject '" + newMsg.getSubject() + "' (older timestamp)");
                    }
                } catch (Exception e) {
                    System.err.println("[Node " + node.getId() + "] Error decoding/processing reply message: " + e.getMessage());
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

