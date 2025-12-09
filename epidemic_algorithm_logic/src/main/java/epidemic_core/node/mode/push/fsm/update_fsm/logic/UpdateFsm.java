package epidemic_core.node.mode.push.fsm.update_fsm.logic;

import epidemic_core.node.mode.push.fsm.update_fsm.logic.output.UpdateFsmResult;
import epidemic_core.node.mode.push.fsm.update_fsm.states.UpdateStates;
import general.fsm.FiniteStateMachine;

public class UpdateFsm extends FiniteStateMachine<UpdateStates> {

    public boolean foundPushMsg;

    public UpdateFsm() {
        super(UpdateStates.IDLE);

        this.foundPushMsg = false;
    }

    public void setFoundPushMsg(boolean foundPushMsg) { this.foundPushMsg = foundPushMsg; }

    public UpdateFsmResult step() {

        // Update time in state
        this.updateTis();

        // Inputs and initial conditions
        UpdateStates currState = this.getState();
        UpdateFsmResult result = new UpdateFsmResult();

        // Transitions
        if(currState == UpdateStates.IDLE && foundPushMsg) {
            this.setNewState(UpdateStates.UPDATE);
        }

        else if(currState == UpdateStates.UPDATE) {
            this.setNewState(UpdateStates.IDLE);
        }

        // Set state
        this.setState();
        currState = this.getState();

        // Compute actions
        if(currState == UpdateStates.IDLE) {
            result.checkPushMsgs = true;
            result.saveMsgs      = false;
        }

        else if(currState == UpdateStates.UPDATE) {
            result.checkPushMsgs = false;
            result.saveMsgs      = true;
        }

        // Reset transition variable
        foundPushMsg = false;

        return result;

    }
}
