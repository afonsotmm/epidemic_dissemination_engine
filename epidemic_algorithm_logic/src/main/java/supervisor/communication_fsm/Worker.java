package supervisor.communication_fsm;

import epidemic_core.node.mode.push.fsm.PushStates;
import general.fsm.FiniteStateMachine;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class CommsFSM {
    FiniteStateMachine<CommsStates> generalFsm = new FiniteStateMachine<>(CommsStates.IDLE);
    BlockingQueue<String> nodeBuffer = new LinkedBlockingQueue<>();
    BlockingQueue<String> uiBuffer = new LinkedBlockingQueue<>();

    public void generalFsmLogic() {

        // Update time in state
        generalFsm.updateTis();
        CommsStates currState = generalFsm.getState();

        // Transitions
        if(currState == CommsStates.IDLE && !uiBuffer.isEmpty()) {
            generalFsm.setNewState(CommsStates.UI_OPS);
        }

        else if(currState == CommsStates.IDLE && uiBuffer.isEmpty() && !nodeBuffer.isEmpty()) {
            generalFsm.setNewState(CommsStates.NODE_OPS);
        }

        // Set state
        generalFsm.setState();

        // Compute actions
        if(generalFsm.getState() == CommsStates.NODE_OPS) {
            commsWithNodes();
        }

        else if(generalFsm.getState() == CommsStates.UI_OPS) {
            commsWithUi();
        }

    }

    public void commsWithNodes(){

    }

    public void commsWithUi(){

    }
}
