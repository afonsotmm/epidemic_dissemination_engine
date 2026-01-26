package supervisor.communication;

import epidemic_core.message.common.Direction;
import epidemic_core.message.common.MessageDispatcher;
import epidemic_core.message.node_to_supervisor.NodeToSupervisorMessageType;
import epidemic_core.message.node_to_supervisor.hello.HelloMsg;
import epidemic_core.message.node_to_supervisor.infection_update.InfectionUpdateMsg;
import epidemic_core.message.node_to_supervisor.remotion_update.RemotionUpdateMsg;
import epidemic_core.message.supervisor_to_ui.SupervisorToUiMessageType;
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
            // Check for UI messages first (priority)
            String uiMsg = uiQueue.poll();
            
            // Update time in state
            generalFsm.updateTis();
            CommsStates currState = generalFsm.getState();

            // Transitions - UI messages have priority
            if(uiMsg != null) {
                generalFsm.setNewState(CommsStates.CONTROL);
                generalFsm.setState();
                manageUiMessages(uiMsg);
                // Return to IDLE after processing UI message
                generalFsm.setNewState(CommsStates.IDLE);
                generalFsm.setState();
            }
            else {
                int processedCount = 0;
                String nodeMsg;
                while ((nodeMsg = nodeQueue.poll()) != null) {
                    if (currState == CommsStates.IDLE) {
                        generalFsm.setNewState(CommsStates.MONITOR);
                        generalFsm.setState();
                        currState = CommsStates.MONITOR;
                    }
                    
                    manageNodeMessages(nodeMsg);
                    processedCount++;
                }
                
                if (processedCount > 0) {
                    generalFsm.setNewState(CommsStates.IDLE);
                    generalFsm.setState();
                }
            }
            
            Thread.onSpinWait();
        }
    }

    public void manageNodeMessages(String msg){
        if (msg == null) {
            return;
        }
        
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(msg);
            
            String direction = jsonNode.has("direction") ? jsonNode.get("direction").asText() : null;
            String messageType = jsonNode.has("messageType") ? jsonNode.get("messageType").asText() : null;
            
            // INFECTION_UPDATE
            if (NodeToSupervisorMessageType.infection_update.toString().equals(messageType) &&
                Direction.node_to_supervisor.toString().equals(direction)) {
                InfectionUpdateMsg nodeMsg = InfectionUpdateMsg.decodeMessage(msg);  // Decode InfectionUpdateMsg from node

                supervisor.ui.SupervisorGui gui = supervisor.getGui();
                if (gui == null || nodeMsg.getTimestamp() == null) {
                    return;
                }
                
                gui.recordInfection(
                    nodeMsg.getUpdatedNodeId(),
                    nodeMsg.getInfectingNodeId(),
                    nodeMsg.getSubject(),
                    nodeMsg.getSourceId(),
                    nodeMsg.getTimestamp().intValue(),
                    nodeMsg.getData()
                );

                // Create InfectionUpdateMsg for external UI
                epidemic_core.message.supervisor_to_ui.infection_update.InfectionUpdateMsg uiMsg = 
                    new epidemic_core.message.supervisor_to_ui.infection_update.InfectionUpdateMsg(
                        Direction.supervisor_to_ui.toString(),
                        SupervisorToUiMessageType.infection_update.toString(),
                        nodeMsg.getUpdatedNodeId(),
                        nodeMsg.getInfectingNodeId(),
                        nodeMsg.getSubject(),
                        nodeMsg.getSourceId(),
                        nodeMsg.getTimestamp(),
                        nodeMsg.getData()
                    );
                
                String encodedMsg = uiMsg.encode();
                supervisor.sendToUi(encodedMsg);
                
            // REMOVAL_UPDATE
            } else if (NodeToSupervisorMessageType.remotion_update.toString().equals(messageType) &&
                       Direction.node_to_supervisor.toString().equals(direction)) {
                
                RemotionUpdateMsg nodeMsg = RemotionUpdateMsg.decodeMessage(msg); // Decode RemotionUpdateMsg from node

                supervisor.ui.SupervisorGui gui = supervisor.getGui();
                if (gui != null && nodeMsg.getTimestamp() != null) {
                    gui.recordRemotion(
                        nodeMsg.getUpdatedNodeId(),
                        nodeMsg.getSubject(),
                        nodeMsg.getSourceId(),
                        nodeMsg.getTimestamp().intValue()
                    );
                }
                
                // Create RemotionUpdateMsg for external UI 
                epidemic_core.message.supervisor_to_ui.remotion_update.RemotionUpdateMsg uiMsg = 
                    new epidemic_core.message.supervisor_to_ui.remotion_update.RemotionUpdateMsg(
                        Direction.supervisor_to_ui.toString(),
                        SupervisorToUiMessageType.remotion_update.toString(),
                        nodeMsg.getUpdatedNodeId(),
                        nodeMsg.getSubject(),
                        nodeMsg.getSourceId(),
                        nodeMsg.getTimestamp()
                    );
                
                String encodedMsg = uiMsg.encode();
                supervisor.sendToUi(encodedMsg);
            
            // HELLO (for distributed deployment mode)
            } else if (NodeToSupervisorMessageType.hello.toString().equals(messageType) &&
                       Direction.node_to_supervisor.toString().equals(direction)) {
                
                HelloMsg helloMsg = HelloMsg.decodeMessage(msg);
                supervisor.handleHelloMsg(helloMsg);
            }
            
        } catch (Exception e) {
            System.err.println("Error processing node message: " + e.getMessage());
            e.printStackTrace();
        }
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
                System.out.println("[Supervisor] Processing StartMsg - initializing network...");
                StartMsg startMsg = StartMsg.decodeMessage(msg);
                supervisor.startNetwork(startMsg);
                System.out.println("[Supervisor] Network initialized successfully!");

            } else if (UiToSupervisorMessageType.end_system.toString().equals(messageType) &&
                       Direction.ui_to_supervisor.toString().equals(direction)) {
                // END_SYSTEM
                EndMsg endMsg = EndMsg.decodeMessage(msg);
                supervisor.endNetwork(endMsg);
            }
            
        } catch (java.io.IOException e) {
            System.err.println("[Worker] Error decoding JSON message: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[Worker] Unexpected error processing UI message: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
