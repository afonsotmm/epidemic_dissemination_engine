package epidemic_core.node.mode.pushpull.fsm.reply_update_fsm.logic;

import epidemic_core.node.mode.pushpull.fsm.reply_update_fsm.logic.output.ReplyUpdateFsmResult;
import epidemic_core.node.mode.pushpull.fsm.reply_update_fsm.states.ReplyUpdateStates;
import general.fsm.FiniteStateMachine;

public class ReplyUpdateFsm extends FiniteStateMachine<ReplyUpdateStates> {

    // Transition Conditions
    private boolean foundReqMsg;

    public ReplyUpdateFsm() {
        super(ReplyUpdateStates.IDLE);

        this.foundReqMsg = false;
    }

    public void setFoundReqMsg(boolean foundReplyMsg) { this.foundReqMsg = foundReplyMsg; }

    public ReplyUpdateFsmResult step() {

        // Update time in state
        this.updateTis();

        // Inputs and initial conditions
        ReplyUpdateFsmResult result = new ReplyUpdateFsmResult();
        ReplyUpdateStates currState = this.getState();

        // Transitions
        if (currState == ReplyUpdateStates.IDLE && foundReqMsg) {
            this.setNewState(ReplyUpdateStates.REPLY);
        }

        else if (currState == ReplyUpdateStates.REPLY) {
            this.setNewState(ReplyUpdateStates.IDLE);
        }

        // Set state
        this.setState();
        currState = this.getState();

        // Compute actions
        if (currState == ReplyUpdateStates.IDLE) {
            result.checkReqMsgs = true;
            result.sendReply    = false;
        }

        else if (currState == ReplyUpdateStates.REPLY) {
            result.checkReqMsgs = false;
            result.sendReply    = true;
        }

        // reset transition variables
        foundReqMsg = false;

        return result;
    }
}
