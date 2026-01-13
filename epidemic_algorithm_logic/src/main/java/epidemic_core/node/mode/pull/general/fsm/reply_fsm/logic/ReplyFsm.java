package epidemic_core.node.mode.pull.general.fsm.reply_fsm.logic;

import epidemic_core.node.mode.pull.general.fsm.reply_fsm.logic.output.ReplyFsmResult;
import epidemic_core.node.mode.pull.general.fsm.reply_fsm.states.ReplyStates;
import general.fsm.FiniteStateMachine;

public class ReplyFsm extends FiniteStateMachine<ReplyStates> {

    // Transition Conditions
    private boolean foundReqMsg;

    public ReplyFsm() {
        super(ReplyStates.IDLE);

        this.foundReqMsg = false;
    }

    public void setFoundReqMsg(boolean foundReplyMsg) { this.foundReqMsg = foundReplyMsg; }

    public ReplyFsmResult step() {

        // Update time in state
        this.updateTis();

        // Inputs and initial conditions
        ReplyFsmResult result = new ReplyFsmResult();
        ReplyStates currState = this.getState();

        // Transitions
        if (currState == ReplyStates.IDLE && foundReqMsg) {
            this.setNewState(ReplyStates.REPLY);
        }

        else if (currState == ReplyStates.REPLY) {
            this.setNewState(ReplyStates.IDLE);
        }

        // Set state
        this.setState();
        currState = this.getState();

        // Compute actions
        if (currState == ReplyStates.IDLE) {
            result.checkReqMsgs = true;
            result.sendReply    = false;
        }

        else if (currState == ReplyStates.REPLY) {
            result.checkReqMsgs = false;
            result.sendReply    = true;
        }

        // reset transition variables
        foundReqMsg = false;

        return result;
    }
}
