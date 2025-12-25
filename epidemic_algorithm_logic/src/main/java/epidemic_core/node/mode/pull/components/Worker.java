package epidemic_core.node.mode.pull.components;

import epidemic_core.message.common.MessageDispatcher;
import epidemic_core.message.node_to_node.request.RequestMsg;
import epidemic_core.message.node_to_node.spread.SpreadMsg;
import epidemic_core.node.mode.pull.PullNode;
import epidemic_core.node.mode.pull.fsm.pull_fsm.logic.PullFsm;
import epidemic_core.node.mode.pull.fsm.pull_fsm.logic.output.PullFsmResult;
import epidemic_core.node.mode.pull.fsm.reply_fsm.logic.ReplyFsm;
import epidemic_core.node.mode.pull.fsm.reply_fsm.logic.output.ReplyFsmResult;
import general.communication.utils.Address;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;

public class Worker {

    private PullNode node;

    private BlockingQueue<String> replyMsgs;
    private List<String> newReplyMsgs;

    private BlockingQueue<String> requestMsgs;
    private List<String> newReqMsgs;

    private BlockingQueue<String> startRoundMsgs;

    private PullFsm pullFsm;
    private ReplyFsm replyFsm;

    private volatile boolean startSignal;

    private final Random rand = new Random();

    public Worker(PullNode node, BlockingQueue<String> replyMsgs, BlockingQueue<String> requestMsgs, BlockingQueue<String> startRoundMsgs) {
        this.node = node;

        this.replyMsgs = replyMsgs;
        this.newReplyMsgs = new ArrayList<>();

        this.requestMsgs = requestMsgs;
        this.newReqMsgs = new ArrayList<>();

        this.startRoundMsgs = startRoundMsgs;

        this.pullFsm = new PullFsm();
        this.replyFsm = new ReplyFsm();

        this.startSignal = false;
    }

    public void workingStep() {
        checkForStartSignal();
        pullFsmHandle();
        replyFsmHandle();
        // Print node state to track message evolution
        node.printNodeState();
    }

    public void workingLoop() {
        while(true) {
            workingStep();

            try {
                Thread.sleep((long) PullNode.RUNNING_INTERVAL);
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
        // Create a REQUEST message with node ID as origin
        RequestMsg requestMsg = new RequestMsg(node.getId());
        String pullString = requestMsg.encode();

        // Get a random neighbour
        List<Integer> neighbours = node.getNeighbours();
        if (neighbours.isEmpty()) {
            System.err.println("[Node " + node.getId() + "] No neighbours to pull from");
            return;
        }

        int randIndex = rand.nextInt(neighbours.size());
        Integer randNeighId = neighbours.get(randIndex);
        Address randNeighAdd = node.getNeighbourAddress(randNeighId);

        if (randNeighAdd != null) {
            node.getCommunication().sendMessage(randNeighAdd, pullString);
        } else {
            System.err.println("Warning: Neighbour " + randNeighId + " address not found");
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
                        Boolean gotStored = node.storeOrIgnoreMessage(spreadMsg);
                        if(!gotStored) {
                            System.out.println("[Node " + node.getId() + "] Ignored message - subject '" + spreadMsg.getId().subject() + "' (older timestamp)");
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
        List<SpreadMsg> storedMessages = node.getAllStoredMessages();

        if (!storedMessages.isEmpty()) {
            try {
                Object decodedMsg = MessageDispatcher.decode(reqMsgStr);
                if (decodedMsg instanceof RequestMsg) {
                    RequestMsg requestMsg = (RequestMsg) decodedMsg;
                    Integer neighId = requestMsg.getOriginId();
                    Address neighAddress = node.getNeighbourAddress(neighId);

                    if (neighAddress != null) {
                        // Send all stored messages as spread
                        for (SpreadMsg message : storedMessages) {
                            String stringMsg = message.encode();
                            node.getCommunication().sendMessage(neighAddress, stringMsg);
                        }
                    } else {
                        System.err.println("Warning: Neighbour " + neighId + " address not found");
                    }
                }
            } catch (Exception e) {
                System.err.println("[Node " + node.getId() + "] Error processing pull reply: " + e.getMessage());
            }
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
