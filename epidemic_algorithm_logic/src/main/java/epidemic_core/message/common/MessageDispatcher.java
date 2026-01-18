package epidemic_core.message.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import epidemic_core.message.node_to_node.feedback.FeedbackMsg;
import epidemic_core.message.node_to_node.initial_request.InitialRequestMsg;
import epidemic_core.message.node_to_node.request.RequestMsg;
import epidemic_core.message.node_to_node.request_and_spread.RequestAndSpreadMsg;
import epidemic_core.message.node_to_node.spread.SpreadMsg;
import epidemic_core.message.node_to_supervisor.hello.HelloMsg;
import epidemic_core.message.supervisor_to_node.start_node.StartNodeMsg;
import epidemic_core.message.supervisor_to_node.kill_node.KillNodeMsg;
import epidemic_core.message.supervisor_to_node.start_round.StartRoundMsg;

import java.io.IOException;

public class MessageDispatcher {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static Object decode(String raw) {
        // detect if it is JSON 
        if (raw.trim().startsWith("{")) {
            return decodeJson(raw);
        } else {
            throw new IllegalArgumentException("Use JSON format.");
        }
    }

    private static Object decodeJson(String jsonString) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonString);
            String direction = jsonNode.has("direction") ? jsonNode.get("direction").asText() : null;
            String messageType = jsonNode.has("messageType") ? jsonNode.get("messageType").asText() : null;

            if ("node_to_node".equals(direction)) {
                return switch (messageType) {
                    case "spread" -> SpreadMsg.decodeMessage(jsonString);
                    case "request" -> RequestMsg.decodeMessage(jsonString);
                    case "initial_request" -> InitialRequestMsg.decodeMessage(jsonString);
                    case "request_and_spread" -> RequestAndSpreadMsg.decodeMessage(jsonString);
                    case "feedback" -> FeedbackMsg.decodeMessage(jsonString);
                    default -> throw new IllegalArgumentException("Unknown node_to_node message type: " + messageType);
                };
            } else if ("supervisor_to_node".equals(direction)) {
                return switch (messageType) {
                    case "start_round" -> StartRoundMsg.decodeMessage(jsonString);
                    case "start_node" -> StartNodeMsg.decodeMessage(jsonString);
                    case "kill_node" -> KillNodeMsg.decodeMessage(jsonString);
                    default -> throw new IllegalArgumentException("Unknown supervisor_to_node message type: " + messageType);
                };
            } else if ("node_to_supervisor".equals(direction)) {
                return switch (messageType) {
                    case "hello" -> HelloMsg.decodeMessage(jsonString);
                    default -> throw new IllegalArgumentException("Unknown node_to_supervisor message type: " + messageType);
                };
            }

            throw new IllegalArgumentException("Unknown direction: " + direction);
        } catch (IOException e) {
            throw new IllegalArgumentException("Error parsing JSON message: " + e.getMessage(), e);
        }
    }

    // Helper methods to check message type
    public static String getDirection(String raw) {
        try {
            if (raw.trim().startsWith("{")) {
                JsonNode jsonNode = objectMapper.readTree(raw);
                return jsonNode.has("direction") ? jsonNode.get("direction").asText() : null;
            }
        } catch (IOException e) {
            
        }
        return null;
    }

    public static String getMessageType(String raw) {
        try {
            if (raw.trim().startsWith("{")) {
                JsonNode jsonNode = objectMapper.readTree(raw);
                return jsonNode.has("messageType") ? jsonNode.get("messageType").asText() : null;
            }
        } catch (IOException e) {

        }
        return null;
    }

    // -------------------------- Node to Node uytils ----------------------------------------------
    public static boolean isNodeToNode(String raw) {
        return "node_to_node".equals(getDirection(raw));
    }

    public static boolean isSpread(String raw) {
        return isNodeToNode(raw) && "spread".equals(getMessageType(raw));
    }

    public static boolean isRequest(String raw) {
        return isNodeToNode(raw) && "request".equals(getMessageType(raw));
    }

    public static boolean isInitialRequest(String raw) {
        return isNodeToNode(raw) && "initial_request".equals(getMessageType(raw));
    }

    public static boolean isRequestAndSpread(String raw) {
        return isNodeToNode(raw) && "request_and_spread".equals(getMessageType(raw));
    }

    public static boolean isFeedback(String raw) {
        return isNodeToNode(raw) && "feedback".equals(getMessageType(raw));
    }
    // ---------------------------------------------------------------------------------------------

    // ------------------------- Supervisor to Node utils ------------------------------------------
    public static boolean isSupervisorToNode(String raw) {
        return "supervisor_to_node".equals(getDirection(raw));
    }

    public static boolean isStartRound(String raw) {
        return isSupervisorToNode(raw) && "start_round".equals(getMessageType(raw));
    }

    public static boolean isStartNode(String raw) {
        return isSupervisorToNode(raw) && "start_node".equals(getMessageType(raw));
    }

    public static boolean isKillNode(String raw) {
        return isSupervisorToNode(raw) && "kill_node".equals(getMessageType(raw));
    }

    // ------------------------- Node to Supervisor utils -----------------------------------------
    public static boolean isNodeToSupervisor(String raw) {
        return "node_to_supervisor".equals(getDirection(raw));
    }

    public static boolean isHello(String raw) {
        return isNodeToSupervisor(raw) && "hello".equals(getMessageType(raw));
    }
    // ---------------------------------------------------------------------------------------------
}
