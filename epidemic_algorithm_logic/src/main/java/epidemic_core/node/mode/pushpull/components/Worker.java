package epidemic_core.node.mode.pushpull.components;

import epidemic_core.message.common.MessageId;
import epidemic_core.message.common.MessageDispatcher;
import epidemic_core.message.node_to_node.request.RequestMsg;
import epidemic_core.message.node_to_node.request_and_spread.RequestAndSpreadMsg;
import epidemic_core.message.node_to_node.spread.SpreadMsg;
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

    private BlockingQueue<String> startRoundMsgs;

    private PushPullFsm pushPullFsm;
    private ReplyUpdateFsm replyUpdateFsm;

    private volatile boolean startSignal;

    private final Random rand = new Random();

    public Worker(PushPullNode node, BlockingQueue<String> replyMsgs, BlockingQueue<String> requestMsgs, BlockingQueue<String> startRoundMsgs) {
        this.node = node;

        this.replyMsgs = replyMsgs;
        this.newReplyMsgs = new ArrayList<>();

        this.requestMsgs = requestMsgs;
        this.newReqMsgs = new ArrayList<>();

        this.startRoundMsgs = startRoundMsgs;

        this.pushPullFsm = new PushPullFsm();
        this.replyUpdateFsm = new ReplyUpdateFsm();

        this.startSignal = false;
    }

    public void workingStep() {
        checkForStartSignal();
        pushPullFsmHandle();
        replyUpdateFsmHandle();
        // Print node state to track message evolution
        node.printNodeState();
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
    //                  START SIGNAL HANDLE                     //
    // ======================================================= //
    public void checkForStartSignal() {
        startSignal = (startRoundMsgs.poll() != null);
    }

    // ======================================================= //
    //                  PUSHPULL FSM HANDLE                    //
    // ======================================================= //
    public void sendPushPullRequest() {
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
            List<SpreadMsg> storedMessages = node.getAllStoredMessages();
            
            if (!storedMessages.isEmpty()) {
                // In pushpull mode, send REQUEST_AND_SPREAD with first message, then send remaining as SPREAD
                // Send REQUEST_AND_SPREAD with the first message
                SpreadMsg firstMsg = storedMessages.get(0);
                MessageId msgId = firstMsg.getId();
                RequestAndSpreadMsg requestAndSpreadMsg = new RequestAndSpreadMsg(
                    msgId,
                    node.getId(),
                    firstMsg.getData()
                );
                
                String requestAndSpreadString = requestAndSpreadMsg.encode();
                node.getCommunication().sendMessage(randNeighAdd, requestAndSpreadString);
                
                // Send remaining messages as SPREAD
                for (int i = 1; i < storedMessages.size(); i++) {
                    SpreadMsg message = storedMessages.get(i);
                    String spreadString = message.encode();
                    node.getCommunication().sendMessage(randNeighAdd, spreadString);
                }
            } else {
                // If no messages, send simple REQUEST to get messages
                RequestMsg requestMsg = new RequestMsg(node.getId());
                String requestString = requestMsg.encode();
                node.getCommunication().sendMessage(randNeighAdd, requestString);
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
                    Object decodedMsg = MessageDispatcher.decode(newMsgStr);
                    if (decodedMsg instanceof SpreadMsg) {
                        SpreadMsg spreadMsg = (SpreadMsg) decodedMsg;
                        Boolean gotStored = node.storeOrIgnoreMessage(spreadMsg);
                        if(!gotStored) {
                            System.out.println("[Node " + node.getId() + "] Ignored message - subject '" + spreadMsg.getId().subject() + "' (older timestamp)");
                        }
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
        List<SpreadMsg> storedMessages = node.getAllStoredMessages();

        if (!storedMessages.isEmpty()) {
            try {
                Object decodedMsg = MessageDispatcher.decode(reqMsgStr);
                // Can receive RequestAndSpreadMsg (RequestMsg shouldn't happen in pushpull, but handle it)
                Integer neighId = null;
                if (decodedMsg instanceof RequestMsg) {
                    RequestMsg requestMsg = (RequestMsg) decodedMsg;
                    neighId = requestMsg.getOriginId();
                } else if (decodedMsg instanceof RequestAndSpreadMsg) {
                    RequestAndSpreadMsg requestAndSpreadMsg = (RequestAndSpreadMsg) decodedMsg;
                    neighId = requestAndSpreadMsg.getOriginId();
                    // Note: The SPREAD part is already processed by the Dispatcher and FSM
                }
                
                if (neighId != null) {
                    Address neighAddress = node.getNeighbourAddress(neighId);

                    if (neighAddress != null) {
                        // Reply with all stored SPREAD messages
                        for (SpreadMsg message : storedMessages) {
                            String stringMsg = message.encode();
                            node.getCommunication().sendMessage(neighAddress, stringMsg);
                        }
                    } else {
                        System.err.println("Warning: Neighbour " + neighId + " address not found");
                    }
                }
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
