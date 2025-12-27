package supervisor.communication;

import epidemic_core.message.ui_to_supervisor.start_round.StartRoundMsg;
import epidemic_core.message.Message;
import epidemic_core.message.msgTypes.HeaderType;
import general.fsm.FiniteStateMachine;
import supervisor.Supervisor;

import java.util.concurrent.BlockingQueue;

public class Worker {
    private FiniteStateMachine<CommsStates> generalFsm = new FiniteStateMachine<>(CommsStates.IDLE);
    private BlockingQueue<String> nodeQueue;
    private BlockingQueue<String> uiQueue;
    private Supervisor supervisor;

    public Worker(Supervisor supervisor, BlockingQueue<String> nodeQueue, BlockingQueue<String> uiQueue) {
        this.supervisor = supervisor;
        this.nodeQueue = nodeQueue;
        this.uiQueue = uiQueue;
    }

    public void generalFsmLogic() {
        String nodeMsg = nodeQueue.poll();
        String uiMsg = uiQueue.poll();

        // Update time in state
        generalFsm.updateTis();
        CommsStates currState = generalFsm.getState();

        // Transitions
        if(currState == CommsStates.IDLE && uiMsg != null) {
            generalFsm.setNewState(CommsStates.CONTROL);
        }

        else if(currState == CommsStates.IDLE && (uiMsg == null) && (nodeMsg != null)) {
            generalFsm.setNewState(CommsStates.MONITOR);
        }

        // Set state
        generalFsm.setState();

        // Compute actions
        if(generalFsm.getState() == CommsStates.MONITOR) {
            manageNodeMessages();
        }

        else if(generalFsm.getState() == CommsStates.CONTROL) { // messages from UI
            manageUiMessages(uiMsg);
        }
    }

    public void manageNodeMessages(){

    }

    public void manageUiMessages(String msg){
        String header = Message.getMessageHeader(msg);
        StartRoundMsg startMsg = StartRoundMsg.decodeMessage(msg);

        if(HeaderType.UI_START.name().equals(header)){
            supervisor.startNetwork(startMsg); // initialize network
        }

//        else if(HeaderType.END.name().equals(header)){
//            // TODO: parar threads dos nós e mandar mensagem para parar
//        }
    }
}
