package epidemic_core.node.mode.push;

import epidemic_core.node.general.Address;
import epidemic_core.node.mode.push.fsm.PushStates;
import epidemic_core.node.mode.push.fsm.UpdateStates;
import epidemic_core.node.general.Node;
import epidemic_core.util.FiniteStateMachine;

import java.util.List;
import java.util.Map;

public class PushNode extends Node {

    private double pushInterval;

    // pushFsm
    FiniteStateMachine<PushStates> pushFsm = new FiniteStateMachine<>(PushStates.IDLE);

    // updateFsm
    FiniteStateMachine<UpdateStates> updateFsm = new FiniteStateMachine<>(UpdateStates.IDLE);

    // Constructor
    public PushNode(Integer id,
                    List<Integer> neighbours,
                    Map<Integer, Address> nodeIdToAddress,
                    List<Map<String, SubjectState>> subjects) {

        super(id, neighbours, nodeIdToAddress, subjects);

    }

    // Fsm Runners
    public void runPushFsm() {

        // Update time in state
        pushFsm.updateTis();
        PushStates currState = pushFsm.getState();

        // Transitions
        if(currState == PushStates.IDLE && pushFsm.checkTimeout(pushInterval)) {
            pushFsm.setNewState(PushStates.PUSH);
        }

        else if(currState == PushStates.PUSH) {
            pushFsm.setNewState(PushStates.IDLE);
        }

        // Set state
        pushFsm.setState();

        // Compute actions
        if(pushFsm.getState() == PushStates.PUSH) {
            // build msg;
            // destination = rand(neighbours);
            // send_msg(dest, msg);
        }

    }

    public void runUpdateFsm() {

        // Update time in state
        updateFsm.updateTis();

        // Inputs and initial conditions
        UpdateStates currState = updateFsm.getState();
        boolean msgReceived = false;

        // Transitions
        if(currState == UpdateStates.IDLE && pushFsm.checkTimeout(pushInterval)) {
            updateFsm.setNewState(UpdateStates.UPDATE);
        }

        else if(currState == UpdateStates.UPDATE) {
            updateFsm.setNewState(UpdateStates.IDLE);
        }

        // Set state
        updateFsm.setState();

        // Compute actions
        if(updateFsm.getState() == UpdateStates.UPDATE) {
            // build msg;
            // destination = rand(neighbours);
            // send_msg(dest, msg);
        }

    }





}
