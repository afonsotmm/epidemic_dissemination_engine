package epidemic_core.node.mode.pushpull.components;

import epidemic_core.message.Message;
import epidemic_core.message.MessageType;
import epidemic_core.node.mode.pushpull.PushPullNode;
import epidemic_core.node.mode.pushpull.fsm.pushpull_fsm.logic.PushPullFsm;
import epidemic_core.node.mode.pushpull.fsm.pushpull_fsm.logic.output.PushPullFsmResult;
import epidemic_core.node.mode.pushpull.fsm.reply_update_fsm.logic.ReplyUpdateFsm;
import epidemic_core.node.mode.pushpull.fsm.reply_update_fsm.logic.output.ReplyUpdateFsmResult;
import general.communication.utils.Address;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;

public class Worker {

    private PushPullNode node;

    private BlockingQueue<String> replyMsgs;
    private List<String> newReplyMsgs;

    private BlockingQueue<String> requestMsgs;
    private List<String> newReqMsgs;

    private PushPullFsm pushPullFsm;
    private ReplyUpdateFsm replyUpdateFsm;

    private volatile boolean startSignal;

    private final Random rand = new Random();

    public Worker(PushPullNode node, BlockingQueue<String> replyMsgs, BlockingQueue<String> requestMsgs) {
        this.node = node;

        this.replyMsgs = replyMsgs;
        this.newReplyMsgs = new ArrayList<>();

        this.requestMsgs = requestMsgs;
        this.newReqMsgs = new ArrayList<>();

        this.pushPullFsm = new PushPullFsm();
        this.replyUpdateFsm = new ReplyUpdateFsm();

        this.startSignal = false;
    }

    public void workingStep() {
        pushPullFsmHandle();
        replyUpdateFsmHandle();
    }

    public void workingLoop() {
        while(true) {
            workingStep();

            try {
                Thread.sleep((long) PushPullNode.RUNNING_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

    }

    public void setStartSignal(boolean startSignal) { this.startSignal = startSignal; }

    // ======================================================= //
    //                  PUSHPULL FSM HANDLE                    //
    // ======================================================= //
    public void sendPushPullRequest() {
        // Send REQUEST with node ID
        Message pullMsg = new Message();
        pullMsg.setData(Integer.toString(node.getId()));
        String pullString = pullMsg.encodeMessage(MessageType.REQUEST);

        // Get a random neighbour
        List<Integer> neighbours = node.getNeighbours();
        if (neighbours.isEmpty()) {
            System.err.println("[Node " + node.getId() + "] No neighbours to pushpull from");
            return;
        }

        int randIndex = rand.nextInt(neighbours.size());
        Integer randNeighId = neighbours.get(randIndex);
        Address randNeighAdd = node.getNeighbourAddress(randNeighId);

        if (randNeighAdd != null) {
            // Send REQUEST
            node.getCommunication().sendMessage(randNeighAdd, pullString);
            
            // In pushpull mode, also send current state (PUSH)
            List<Message> storedMessages = node.getAllStoredMessages();
            if (!storedMessages.isEmpty()) {
                // Send a random stored message (current state)
                int msgIndex = rand.nextInt(storedMessages.size());
                Message stateMsg = storedMessages.get(msgIndex);
                String pushString = stateMsg.encodeMessage(MessageType.PUSH);
                node.getCommunication().sendMessage(randNeighAdd, pushString);
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
                    Message newMsg = Message.decodeMessage(newMsgStr);
                    Boolean gotStored = node.storeOrIgnoreMessage(newMsg);
                    if(!gotStored) {
                        System.out.println("[Node " + node.getId() + "] Ignored message - subject '" + newMsg.getSubject() + "' (older timestamp)");
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
        List<Message> storedMessages = node.getAllStoredMessages();

        if (!storedMessages.isEmpty()) {
            try {
                Message receivedMessage = Message.decodeMessage(reqMsgStr);
                Integer neighId = Integer.valueOf(receivedMessage.getData());
                Address neighAddress = node.getNeighbourAddress(neighId);

                if (neighAddress != null) {
                    // In pushpull mode, send REPLY with a random stored message
                    int randIndex = rand.nextInt(storedMessages.size());
                    Message message = storedMessages.get(randIndex);
                    String stringMsg = message.encodeMessage(MessageType.REPLY);
                    node.getCommunication().sendMessage(neighAddress, stringMsg);
                    
                    // Also send current state as PUSH
                    // (Note: if we already sent one above, we could optimize, but for now we send another)
                    int pushIndex = rand.nextInt(storedMessages.size());
                    Message pushMsg = storedMessages.get(pushIndex);
                    String pushString = pushMsg.encodeMessage(MessageType.PUSH);
                    node.getCommunication().sendMessage(neighAddress, pushString);
                } else {
                    System.err.println("Warning: Neighbour " + neighId + " address not found");
                }
            } catch (NumberFormatException e) {
                System.err.println("[Node " + node.getId() + "] Error parsing neighbour ID from request message: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("[Node " + node.getId() + "] Error processing pushpull reply: " + e.getMessage());
            }
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

            // Replying every request received
            for(String newReqMsgStr: newReqMsgs) {
                sendPushPullReply(newReqMsgStr);
            }
            
            newReqMsgs.clear();
        }

    }
}
