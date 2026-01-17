package supervisor.communication;

import epidemic_core.message.common.Direction;
import epidemic_core.message.common.MessageDispatcher;

import java.util.concurrent.BlockingQueue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**	
 * Responsible for dispatching messages from the msgsQueue to the nodeQueue or uiQueue
 */

public class Dispatcher {
    private BlockingQueue<String> msgsQueue;
    private BlockingQueue<String> nodeQueue;
    private BlockingQueue<String> uiQueue;
    private ObjectMapper objectMapper;

    public Dispatcher(BlockingQueue<String> msgsQueue, BlockingQueue<String> nodeQueue, BlockingQueue<String> uiQueue) {
        this.msgsQueue = msgsQueue;
        this.nodeQueue = nodeQueue;
        this.uiQueue = uiQueue;
        this.objectMapper = new ObjectMapper();
    }

    public void dispatchingLoop(){
        while(true) {
        try {
            String consumedMsg = msgsQueue.take();
            String direction = getDirection(consumedMsg);
            String messageType = getMessageType(consumedMsg);

                if(Direction.ui_to_supervisor.toString().equals(direction) && isUiMessageType(messageType)){
                    System.out.println("[Supervisor] Received UI message: " + messageType);
                    uiQueue.put(consumedMsg);
                }
                else if(Direction.node_to_supervisor.toString().equals(direction) && isNodeMessageType(messageType)){
                    System.out.println("[Supervisor] Received node message: " + messageType);
                    nodeQueue.put(consumedMsg);
                }

                Thread.onSpinWait();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Error dispatching message: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private String getDirection(String msg) {
        if (msg.trim().startsWith("{")) {
            try {
                JsonNode jsonNode = objectMapper.readTree(msg);
                if (jsonNode.has("direction")) {
                    return jsonNode.get("direction").asText();
                }
            } catch (Exception e) {
            }
        }

        return MessageDispatcher.getDirection(msg);
    }

    private String getMessageType(String msg) {
        if (msg.trim().startsWith("{")) {
            try {
                JsonNode jsonNode = objectMapper.readTree(msg);
                if (jsonNode.has("messageType")) {
                    return jsonNode.get("messageType").asText();
                }
            } catch (Exception e) {
            }
        }

        return MessageDispatcher.getMessageType(msg);
    }

    private boolean isUiMessageType(String messageType) {
        if (messageType == null) return false;
        return "start_system".equals(messageType) || "end_system".equals(messageType);
    }

    private boolean isNodeMessageType(String messageType) {
        if (messageType == null) return false;
        return "infection_update".equals(messageType) || "remotion_update".equals(messageType);
    }
}
