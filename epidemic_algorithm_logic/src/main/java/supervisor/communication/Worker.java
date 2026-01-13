package supervisor.communication;

import epidemic_core.message.common.Direction;
import epidemic_core.message.ui_to_supervisor.UiToSupervisorMessageType;
import epidemic_core.message.ui_to_supervisor.end_system.EndMsg;
import epidemic_core.message.ui_to_supervisor.start_system.StartMsg;
import general.fsm.FiniteStateMachine;
import supervisor.Supervisor;

import java.util.concurrent.BlockingQueue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**	
 * Processes messages from UI and node and delegates network control actions to the supervisor
 */

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
        while(true) {
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
            
            Thread.onSpinWait();
        }
    }

    public void manageNodeMessages(){

    }

    public void manageUiMessages(String msg){
        if (msg == null) return;
        
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(msg);
            
            String direction = jsonNode.has("direction") ? jsonNode.get("direction").asText() : null;
            String messageType = jsonNode.has("messageType") ? jsonNode.get("messageType").asText() : null;
            
            if (UiToSupervisorMessageType.start_system.toString().equals(messageType) && 
                Direction.ui_to_supervisor.toString().equals(direction)) {
                // START_SYSTEM
                StartMsg startMsg = StartMsg.decodeMessage(msg);
                supervisor.startNetwork(startMsg);

            } else if (UiToSupervisorMessageType.end_system.toString().equals(messageType) &&
                       Direction.ui_to_supervisor.toString().equals(direction)) {
                // END_SYSTEM
                EndMsg endMsg = EndMsg.decodeMessage(msg);
                supervisor.endNetwork(endMsg);
            }
            
        } catch (java.io.IOException e) {
            System.err.println("Error decoding JSON message: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
