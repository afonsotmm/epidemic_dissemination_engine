package epidemic_core.node.mode.pull;

import epidemic_core.message.Message;
import epidemic_core.message.MessageType;
import epidemic_core.node.mode.pull.fsm.PullStates;
import epidemic_core.node.mode.pull.fsm.ReplyStates;
import general.communication.utils.Address;
import epidemic_core.node.Node;

import general.fsm.FiniteStateMachine;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// =================================================================
//                     Guide to use this class
// =================================================================
//
//
// =================================================================

public class PullNode extends Node {

    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public static final double RUNNING_INTERVAL = 50; // milliseconds
    public static final double TIMEOUT = 5000;
    private double pullInterval; // seconds

    private volatile String pullAuxMsg;
    private volatile String replyAuxMsg;

    // Constructor
    public PullNode(Integer id,
                    List<Integer> neighbours,
                    String assignedSubjectAsSource,
                    Map<Integer, Address> nodeIdToAddressTable,
                    double pushInterval,
                    Address supervisorAddress) {

        super(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable, supervisorAddress);
        this.pullInterval = pushInterval;
        pullAuxMsg = null;
    }

    // ===========================================================
    //                      PULL FSM
    // ===========================================================
    FiniteStateMachine<PullStates> pullFsm = new FiniteStateMachine<>(PullStates.IDLE);

    public void pullFsmLogic() {

        // Update time in state
        pullFsm.updateTis();
        PullStates currState = pullFsm.getState();

        // Transitions
        if(currState == PullStates.IDLE && pullFsm.checkTimeout(pullInterval)) {
            pullFsm.setNewState(PullStates.PULL);
        }

        else if(currState == PullStates.PULL) {
            pullFsm.setNewState(PullStates.WAIT);
        }

        else if(currState == PullStates.WAIT && pullAuxMsg != null) {
            pullFsm.setNewState(PullStates.PROCESS);
        }

        else if(currState == PullStates.WAIT && pullFsm.checkTimeout(TIMEOUT)) {
            pullFsm.setNewState(PullStates.IDLE);
        }

        else if(currState == PullStates.PROCESS) {
            pullFsm.setNewState(PullStates.IDLE);
        }

        // Set state
        pullFsm.setState();

        // Compute actions
        if(pullFsm.getState() == PullStates.PULL) {

            Message pullMsg = new Message();
            // TODO: Make a specific message to pull requests
            // TODO: Maybe include origin address in every msg (not in data!)
            pullMsg.setData(Integer.toString(getId()));
            String pullString = pullMsg.encodeMessage(MessageType.REQUEST);

            // Get a random neighbour
            // TODO: Make this a Node method (to avoid duplication)
            List<Integer> neighbours = getNeighbours();
            Random rand = new Random();
            int randIndex = rand.nextInt(neighbours.size());
            Integer randNeighId = neighbours.get(randIndex);
            Address randNeighAdd = getNeighbourAddress(randNeighId);

            // Send Message only if address is valid
            if (randNeighAdd != null) {
                getCommunication().sendMessage(randNeighAdd, pullString);
            } else {
                System.err.println("Warning: Neighbour " + randNeighId + " address not found");
            }

        }

        else if(pullFsm.getState() == PullStates.WAIT) {
            pullAuxMsg = getCommunication().receiveMessage();
        }

        else if(pullFsm.getState() == PullStates.PROCESS) {
            // Process the received message
            if (pullAuxMsg != null) {
                String header = Message.getMessageHeader(pullAuxMsg);
                if(MessageType.REPLY.name().equals(header)) {
                    Message receivedMessage = Message.decodeMessage(pullAuxMsg);
                    Boolean gotStored = storeOrIgnoreMessage(receivedMessage);

                    if (gotStored) {
                        // Log will be printed in storeOrIgnoreMessage
                    } else {
                        System.out.println("[Node " + getId() + "] Ignored message - subject '" +
                                receivedMessage.getSubject() + "' (older timestamp)");
                    }
                } else {
                    System.out.println("[Node " + getId() + "] Ignored message - not a 'REPLY' as expected (got: " + header + ")");
                }
                pullAuxMsg = null;
            }
        }

    }

    public void runPullFsm() {
        scheduler.scheduleAtFixedRate(
                this::pullFsmLogic,
                0,
                (long)RUNNING_INTERVAL,
                TimeUnit.MILLISECONDS);
    }

    // ===========================================================
    //                    REPLY FSM
    // ===========================================================
    FiniteStateMachine<ReplyStates> updateFsm = new FiniteStateMachine<>(ReplyStates.IDLE);

    public void replyFsmLogic() {

        // Update time in state
        updateFsm.updateTis();

        // Inputs and initial conditions
        ReplyStates currState = updateFsm.getState();

        // Transitions
        if(currState == ReplyStates.IDLE && replyAuxMsg != null) {
            updateFsm.setNewState(ReplyStates.REPLY);
        }

        else if(currState == ReplyStates.REPLY) {
            updateFsm.setNewState(ReplyStates.IDLE);
        }

        // Set state
        updateFsm.setState();

        // Compute actions
        if(updateFsm.getState() == ReplyStates.IDLE) {
            replyAuxMsg = getCommunication().receiveMessage();
        }

        else if(updateFsm.getState() == ReplyStates.REPLY) {
            // TODO: Improve the way of extracting the Header of a given message
            String header = Message.getMessageHeader(replyAuxMsg);
            if(MessageType.REQUEST.name().equals(header)) {
                List<Message> storedMessages = getAllStoredMessages();

                if (!storedMessages.isEmpty()) {
                    Message receivedMessage = Message.decodeMessage(replyAuxMsg);
                    Integer neighId = Integer.valueOf(receivedMessage.getData());
                    Address neighAddress = getNeighbourAddress(neighId);
                    
                    if (neighAddress != null) {
                        // Send a random message
                        Random rand = new Random();
                        int randIndex = rand.nextInt(storedMessages.size());
                        Message message = storedMessages.get(randIndex);
                        String stringMsg = message.encodeMessage(MessageType.REPLY);
                        getCommunication().sendMessage(neighAddress, stringMsg);
                    } else {
                        System.err.println("Warning: Neighbour " + neighId + " address not found");
                    }
                }
            }
            replyAuxMsg = null;
        }

    }

    public void runReplyFsm() {
        scheduler.scheduleAtFixedRate(
                this::replyFsmLogic,
                0,
                (long)RUNNING_INTERVAL,
                TimeUnit.MILLISECONDS);
    }

    // ===========================================================
    //                        RUNNER
    // ===========================================================
    public void startRunning() {
        runPullFsm();
        runReplyFsm();
    }

}

