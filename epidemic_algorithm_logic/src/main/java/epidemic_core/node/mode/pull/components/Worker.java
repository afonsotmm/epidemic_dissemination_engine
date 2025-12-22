package epidemic_core.node.mode.pull.components;

import epidemic_core.message.Message;
import epidemic_core.message.msgTypes.NodeToNode;
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

    private PullFsm pullFsm;
    private ReplyFsm replyFsm;

    private volatile boolean startSignal;

    private final Random rand = new Random();

    public Worker(PullNode node, BlockingQueue<String> replyMsgs, BlockingQueue<String> requestMsgs) {
        this.node = node;

        this.replyMsgs = replyMsgs;
        this.newReplyMsgs = new ArrayList<>();

        this.requestMsgs = requestMsgs;
        this.newReqMsgs = new ArrayList<>();

        this.pullFsm = new PullFsm();
        this.replyFsm = new ReplyFsm();

        this.startSignal = false;
    }

    public void workingStep() {
        pullFsmHandle();
        replyFsmHandle();
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
    //                  PULL FSM HANDLE                        //
    // ======================================================= //
    public void sendPullRequest() {
        Message pullMsg = new Message();
        // TODO: Make a specific message to pull requests
        // TODO: Maybe include origin address in every msg (not in data!)
        pullMsg.setData(Integer.toString(node.getId()));
        String pullString = pullMsg.encodeMessage(NodeToNode.REQUEST);

        // Get a random neighbour
        // TODO: Make this a Node method (to avoid duplication)
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
                    Message newMsg = Message.decodeMessage(newMsgStr);
                    Boolean gotStored = node.storeOrIgnoreMessage(newMsg);
                    if(!gotStored) {
                        System.out.println("[Node " + node.getId() + "] Ignored message - subject '" + newMsg.getSubject() + "' (older timestamp)");
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
        // TODO: IMPROVE (now It sends a random message)
        List<Message> storedMessages = node.getAllStoredMessages();

        if (!storedMessages.isEmpty()) {
            try {
                Message receivedMessage = Message.decodeMessage(reqMsgStr);
                Integer neighId = Integer.valueOf(receivedMessage.getData());
                Address neighAddress = node.getNeighbourAddress(neighId);

                if (neighAddress != null) {
                    // Send a random message
                    int randIndex = rand.nextInt(storedMessages.size());
                    Message message = storedMessages.get(randIndex);
                    String stringMsg = message.encodeMessage(NodeToNode.REPLY);
                    node.getCommunication().sendMessage(neighAddress, stringMsg);
                } else {
                    System.err.println("Warning: Neighbour " + neighId + " address not found");
                }
            } catch (NumberFormatException e) {
                System.err.println("[Node " + node.getId() + "] Error parsing neighbour ID from request message: " + e.getMessage());
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
