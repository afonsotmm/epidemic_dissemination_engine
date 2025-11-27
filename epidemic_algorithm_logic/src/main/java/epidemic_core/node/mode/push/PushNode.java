package epidemic_core.node.mode.push;

import epidemic_core.message.Message;
import epidemic_core.message.MessageType;
import general.communication.utils.Address;
import epidemic_core.node.mode.push.fsm.PushStates;
import epidemic_core.node.mode.push.fsm.UpdateStates;
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
// 1. PushNode node = new PushNode(id, neighbours, nodeIdToAddress);
// 2. node.startRunning();
// =================================================================

public class PushNode extends Node {

    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public static final double RUNNING_INTERVAL = 50;
    public static final double PUSH_INTERVAL = 5000;

    // Constructor
    public PushNode(Integer id,
                    List<Integer> neighbours,
                    String assignedSubjectAsSource,
                    Map<Integer, Address> nodeIdToAddressTable,
                    double pushInterval) {

        super(id, neighbours, assignedSubjectAsSource, nodeIdToAddressTable);
    }

    // ===========================================================
    //                      PUSH FSM
    // ===========================================================
    FiniteStateMachine<PushStates> pushFsm = new FiniteStateMachine<>(PushStates.IDLE);

    public void pushFsmLogic() {

        // Update time in state
        pushFsm.updateTis();
        PushStates currState = pushFsm.getState();

        // Transitions
        if(currState == PushStates.IDLE && pushFsm.checkTimeout(PUSH_INTERVAL)) {
            pushFsm.setNewState(PushStates.PUSH);
        }

        else if(currState == PushStates.PUSH) {
            pushFsm.setNewState(PushStates.IDLE);
        }

        // Set state
        pushFsm.setState();

        // Compute actions
        if(pushFsm.getState() == PushStates.PUSH) {

            List<Message> storedMessages = getAllStoredMessages();

            for(Message message : storedMessages) {
                // Message Encoding
                String stringMsg = message.encodeMessage(MessageType.REQUEST);
                // Random Destinations Neighbour
                Random rand  = new Random();
                int randNeighId = rand.nextInt(getNeighbours().size());
                Address randNeighAdd = getNeighbourAddress(randNeighId);
                // Send Message
                getCommunication().sendMessage(randNeighAdd, stringMsg);
            }
        }

    }

    public void runPushFsm() {
        scheduler.scheduleAtFixedRate(
                this::pushFsmLogic,
                0,
                (long)RUNNING_INTERVAL,
                TimeUnit.MILLISECONDS);
    }

    // ===========================================================
    //                    UPDATE FSM
    // ===========================================================
    FiniteStateMachine<UpdateStates> updateFsm = new FiniteStateMachine<>(UpdateStates.IDLE);

    public void updateFsmLogic() {

        // Update time in state
        updateFsm.updateTis();

        // Inputs and initial conditions
        UpdateStates currState = updateFsm.getState();

        // Transitions
        if(currState == UpdateStates.IDLE && updateFsm.checkTimeout(PUSH_INTERVAL)) {
            updateFsm.setNewState(UpdateStates.UPDATE);
        }

        else if(currState == UpdateStates.UPDATE) {
            updateFsm.setNewState(UpdateStates.IDLE);
        }

        // Set state
        updateFsm.setState();

        // Compute actions
        if(updateFsm.getState() == UpdateStates.UPDATE) {
            // TODO
        }

    }

    public void runUpdateFsm() {
        scheduler.scheduleAtFixedRate(
                this::updateFsmLogic,
                0,
                (long)RUNNING_INTERVAL,
                TimeUnit.MILLISECONDS);
    }

    // ===========================================================
    //                        RUNNER
    // ===========================================================
    public void startRunning() {
        runPushFsm();
        runUpdateFsm();
    }

}
