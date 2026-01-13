package epidemic_core.message.common;

import epidemic_core.message.node_to_node.feedback.FeedbackMsg;
import epidemic_core.message.node_to_node.initial_request.InitialRequestMsg;
import epidemic_core.message.node_to_node.request.RequestMsg;
import epidemic_core.message.node_to_node.request_and_spread.RequestAndSpreadMsg;
import epidemic_core.message.node_to_node.spread.SpreadMsg;

public class MessageDispatcher {

    public static Object decode(String raw) {

        String[] parts = raw.split(";");

        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid message");
        }

        String direction = parts[0];
        String type = parts[1];

        if (direction.equals("node_to_node")) {

            return switch (type) {
                case "spread" -> SpreadMsg.decode(raw);
                case "request" -> RequestMsg.decode(raw);
                case "initial_request" -> InitialRequestMsg.decode(raw);
                case "request_and_spread" -> RequestAndSpreadMsg.decode(raw);
                case "feedback" -> FeedbackMsg.decode(raw);
                default -> throw new IllegalArgumentException("Unknown node_to_node message");
            };

        } else if(direction.equals("supervisor_to_node")) {

            return null;

        } else if(direction.equals("node_to_supervisor")) {

            return null;

        }

        throw new IllegalArgumentException("Unknown direction");

    }

    // Helper methods to check message type
    public static String getDirection(String raw) {
        String[] parts = raw.split(";");
        if (parts.length < 1) {
            return null;
        }
        return parts[0];
    }

    public static String getMessageType(String raw) {
        String[] parts = raw.split(";");
        if (parts.length < 2) {
            return null;
        }
        return parts[1];
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
    // ---------------------------------------------------------------------------------------------
}
