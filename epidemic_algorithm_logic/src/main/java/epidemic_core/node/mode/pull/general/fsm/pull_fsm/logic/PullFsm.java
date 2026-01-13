package epidemic_core.node.mode.pull.general.fsm.pull_fsm.logic;

import epidemic_core.node.mode.pull.general.fsm.pull_fsm.logic.output.PullFsmResult;
import epidemic_core.node.mode.pull.general.fsm.pull_fsm.states.PullStates;
import general.fsm.FiniteStateMachine;

public class PullFsm extends FiniteStateMachine<PullStates> {

    // Transition Conditions
    private boolean startSignal;
    private boolean foundReplyMsg;

    public PullFsm() {
        super(PullStates.IDLE);

        this.startSignal = false;
        this.foundReplyMsg = false;
    }

    public void setStartSignal(boolean startSignal) { this.startSignal = startSignal; }
    public void setFoundReplyMsg(boolean foundReplyMsg) { this.foundReplyMsg = foundReplyMsg; }

    public PullFsmResult step() {

        // Update time in state
        this.updateTis();

        // Inputs and initial conditions
        PullFsmResult result = new PullFsmResult();
        PullStates currState = this.getState();

        // Transitions
        if (currState == PullStates.IDLE && startSignal) {
            this.setNewState(PullStates.PULL);
        }

        else if (currState == PullStates.IDLE && foundReplyMsg && !startSignal) {
            this.setNewState(PullStates.PROCESS);
        }

        else if (currState == PullStates.PULL) {
            this.setNewState(PullStates.IDLE);
        }

        else if (currState == PullStates.PROCESS) {
            this.setNewState(PullStates.IDLE);
        }

        // Set state
        this.setState();
        currState = this.getState();

        // Compute actions
        if (currState == PullStates.IDLE) {
            result.pullReq        = false;
            result.updateStatus   = false;
            result.checkReplyMsgs = true;
            result.saveReplyMsgs  = false;
        }

        else if (currState == PullStates.PULL) {
            result.pullReq        = true;
            result.updateStatus   = true;
            result.checkReplyMsgs = false;
            result.saveReplyMsgs  = false;
        }

        else if(currState == PullStates.PROCESS) {
            result.pullReq        = false;
            result.updateStatus   = false;
            result.checkReplyMsgs = false;
            result.saveReplyMsgs  = true;
        }

        // reset transition variables
        startSignal = false;
        foundReplyMsg = false;

        return result;
    }
}
