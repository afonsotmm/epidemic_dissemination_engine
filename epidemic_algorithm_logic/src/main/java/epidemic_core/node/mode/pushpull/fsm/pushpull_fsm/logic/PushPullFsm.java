package epidemic_core.node.mode.pushpull.fsm.pushpull_fsm.logic;

import epidemic_core.node.mode.pushpull.fsm.pushpull_fsm.logic.output.PushPullFsmResult;
import epidemic_core.node.mode.pushpull.fsm.pushpull_fsm.states.PushPullStates;
import general.fsm.FiniteStateMachine;

public class PushPullFsm extends FiniteStateMachine<PushPullStates> {

    // Transition Conditions
    private boolean startSignal;
    private boolean foundReplyMsg;

    public PushPullFsm() {
        super(PushPullStates.IDLE);

        this.startSignal = false;
        this.foundReplyMsg = false;
    }

    public void setStartSignal(boolean startSignal) { this.startSignal = startSignal; }
    public void setFoundReplyMsg(boolean foundReplyMsg) { this.foundReplyMsg = foundReplyMsg; }

    public PushPullFsmResult step() {

        // Update time in state
        this.updateTis();

        // Inputs and initial conditions
        PushPullFsmResult result = new PushPullFsmResult();
        PushPullStates currState = this.getState();

        // Transitions
        if (currState == PushPullStates.IDLE && startSignal) {
            this.setNewState(PushPullStates.PUSHPULL);
        }

        else if (currState == PushPullStates.IDLE && foundReplyMsg && !startSignal) {
            this.setNewState(PushPullStates.PROCESS);
        }

        else if (currState == PushPullStates.PUSHPULL) {
            this.setNewState(PushPullStates.IDLE);
        }

        else if (currState == PushPullStates.PROCESS) {
            this.setNewState(PushPullStates.IDLE);
        }

        // Set state
        this.setState();
        currState = this.getState();

        // Compute actions
        if (currState == PushPullStates.IDLE) {
            result.pushPullReq = false;
            result.updateStatus = false;
            result.checkReplyMsgs = true;
            result.saveReplyMsgs = false;
        } else if (currState == PushPullStates.PUSHPULL) {
            result.pushPullReq = true;
            result.updateStatus = true;
            result.checkReplyMsgs = false;
            result.saveReplyMsgs = false;
        } else if (currState == PushPullStates.PROCESS) {
            result.pushPullReq = false;
            result.updateStatus = false;
            result.checkReplyMsgs = false;
            result.saveReplyMsgs = true;
        }

        // reset transition variables
        startSignal = false;
        foundReplyMsg = false;

        return result;
    }
}