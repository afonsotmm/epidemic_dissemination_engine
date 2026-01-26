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
    private BlockingQueue<String> udpMsgsQueue;  // Messages from UDP (nodes)
    private BlockingQueue<String> tcpMsgsQueue;  // Messages from TCP (UI)
    private BlockingQueue<String> nodeQueue;
    private BlockingQueue<String> uiQueue;
    private ObjectMapper objectMapper;

    public Dispatcher(BlockingQueue<String> udpMsgsQueue, BlockingQueue<String> tcpMsgsQueue, 
                      BlockingQueue<String> nodeQueue, BlockingQueue<String> uiQueue) {
        this.udpMsgsQueue = udpMsgsQueue;
        this.tcpMsgsQueue = tcpMsgsQueue;
        this.nodeQueue = nodeQueue;
        this.uiQueue = uiQueue;
        this.objectMapper = new ObjectMapper();
    }

    public void dispatchingLoop(){
        while(true) {
            try {
                String consumedMsg = tcpMsgsQueue.poll();

                if (consumedMsg == null) {
                    consumedMsg = udpMsgsQueue.poll();
                }

                if (consumedMsg != null) {
                    String direction = getDirection(consumedMsg);
                    String messageType = getMessageType(consumedMsg);

                    if(Direction.ui_to_supervisor.toString().equals(direction) && isUiMessageType(messageType)){
                        System.out.println("[Dispatcher] Routing UI message to uiQueue: " + messageType);
                        uiQueue.put(consumedMsg);
                    }
                    else if(Direction.node_to_supervisor.toString().equals(direction) && isNodeMessageType(messageType)){
                        System.out.println("[Dispatcher] Routing node message to nodeQueue: " + messageType);
                        nodeQueue.put(consumedMsg);
                    }
                    else {
                        System.out.println("[Dispatcher] WARNING: Message not routed! direction=" + direction + ", messageType=" + messageType);
                    }
                } else {
                    Thread.sleep(1);
                }

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
        return "infection_update".equals(messageType) || 
               "remotion_update".equals(messageType) || 
               "hello".equals(messageType);
    }
}
