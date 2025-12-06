package supervisor.communication_fsm;

import general.fsm.FiniteStateMachine;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Worker {
    FiniteStateMachine<CommsStates> generalFsm = new FiniteStateMachine<>(CommsStates.IDLE);
    BlockingQueue<String> nodeBuffer = new LinkedBlockingQueue<>();
    BlockingQueue<String> uiBuffer = new LinkedBlockingQueue<>();

    public void generalFsmLogic() {

        // Update time in state
        generalFsm.updateTis();
        CommsStates currState = generalFsm.getState();

        // Transitions
        if(currState == CommsStates.IDLE && !uiBuffer.isEmpty()) {
            generalFsm.setNewState(CommsStates.CONTROL);
        }

        else if(currState == CommsStates.IDLE && uiBuffer.isEmpty() && !nodeBuffer.isEmpty()) {
            generalFsm.setNewState(CommsStates.MONITOR);
        }

        // Set state
        generalFsm.setState();

        // Compute actions
        if(generalFsm.getState() == CommsStates.MONITOR) {
            manageNodeMessages();
        }

        else if(generalFsm.getState() == CommsStates.CONTROL) {
            manageUiMessages();
        }

    }

    public void manageNodeMessages(){

    }

    public void manageUiMessages(){

    }
}
