package epidemic_core.node.mode.push.general.fsm.push_fsm.logic;

import epidemic_core.node.mode.push.general.fsm.push_fsm.logic.output.PushFsmResult;
import epidemic_core.node.mode.push.general.fsm.push_fsm.states.PushStates;

import general.fsm.FiniteStateMachine;

public class PushFsm extends FiniteStateMachine<PushStates> {

    // Transition Conditions
    private boolean startSignal;

    public PushFsm() {
        super(PushStates.IDLE);

        this.startSignal = false;
    }

    public void setStartSignal(boolean startSignal) { this.startSignal = startSignal; }

    public PushFsmResult step() {

        // Update time in state
        this.updateTis();

        // Inputs and initial conditions
        PushFsmResult result = new PushFsmResult();
        PushStates currState = this.getState();

        // Transitions
        if(currState == PushStates.IDLE && startSignal) {
            this.setNewState(PushStates.PUSH);
        }

        else if(currState == PushStates.PUSH) {
            this.setNewState(PushStates.IDLE);
        }

        // Set state
        this.setState();
        currState = this.getState();

        // Compute actions
        if(currState == PushStates.IDLE) {
            result.sendPush     = false;
            result.updateStatus = false;
        }

        else if(currState == PushStates.PUSH) {
            result.sendPush     = true;
            result.updateStatus = true;
        }

        // Reset transition variable
        startSignal = false;

        return result;

    }
}
