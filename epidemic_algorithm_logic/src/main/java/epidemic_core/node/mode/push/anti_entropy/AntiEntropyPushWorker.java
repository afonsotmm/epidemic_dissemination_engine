package epidemic_core.node.mode.push.anti_entropy;

import epidemic_core.message.common.MessageDispatcher;
import epidemic_core.message.node_to_node.spread.SpreadMsg;
import epidemic_core.node.mode.push.general.components.WorkerInterface;
import epidemic_core.node.mode.push.general.fsm.push_fsm.logic.PushFsm;
import epidemic_core.node.mode.push.general.fsm.push_fsm.logic.output.PushFsmResult;
import epidemic_core.node.mode.push.general.fsm.update_fsm.logic.UpdateFsm;
import epidemic_core.node.mode.push.general.fsm.update_fsm.logic.output.UpdateFsmResult;
import general.communication.utils.Address;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;

public class AntiEntropyPushWorker implements WorkerInterface {

    private AntiEntropyPushNode node;

    private BlockingQueue<String> pushMsgs;
    private List<String> newPushMsgs;

    private BlockingQueue<String> startRoundMsgs;

    private PushFsm pushFsm;
    private UpdateFsm updateFsm;

    private volatile boolean startSignal;

    private final Random rand = new Random();

    public AntiEntropyPushWorker(AntiEntropyPushNode node, BlockingQueue<String> pushMsgs, BlockingQueue<String> startRoundMsgs) {
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
        // Print node state removed - too verbose
        // node.printNodeState();
    }

    public void workingLoop() {
        while(node.isRunning()) {
            workingStep();

            try {
                Thread.sleep((long) AntiEntropyPushNode.RUNNING_INTERVAL);
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
        if (startRoundMsgs.poll() != null) {
            startSignal = true;
            System.out.println("[Node " + node.getId() + "] StartRoundMsg processed - starting push round");
        } else {
            startSignal = false;
        }
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
                    // Create new SpreadMsg with updated originId (this node is now the origin)
                    epidemic_core.message.common.MessageId msgId = message.getId();
                    SpreadMsg forwardMsg = new SpreadMsg(
                        epidemic_core.message.common.Direction.node_to_node.toString(),
                        epidemic_core.message.node_to_node.NodeToNodeMessageType.spread.toString(),
                        msgId.topic().subject(),
                        msgId.topic().sourceId(),
                        msgId.timestamp(),
                        node.getId(), // Update originId to this node (who is forwarding)
                        message.getData()
                    );
                    
                    try {
                        String stringMsg = forwardMsg.encode();
                        node.getCommunication().sendMessage(randNeighAdd, stringMsg);
                        System.out.println("[Node " + node.getId() + "] Sent SpreadMsg (subject='" + msgId.topic().subject() + "', sourceId=" + msgId.topic().sourceId() + ") to neighbor " + randNeighId + " at " + randNeighAdd);
                    } catch (java.io.IOException e) {
                        System.err.println("[Node " + node.getId() + "] Error encoding/sending SpreadMsg: " + e.getMessage());
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
