package epidemic_core.node.mode.pushpull.fsm.pushpull_fsm.logic.output;

import epidemic_core.node.mode.pushpull.fsm.pushpull_fsm.states.PushPullStates;

public class PushPullFsmResult {
    public boolean pushPullReq = false;
    public boolean updateStatus = false;
    public boolean checkReplyMsgs = false;
    public boolean saveReplyMsgs = false;
}
