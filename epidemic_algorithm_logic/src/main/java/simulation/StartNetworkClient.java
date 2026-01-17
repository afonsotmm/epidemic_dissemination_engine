package simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import general.communication.Communication;
import general.communication.implementation.UdpCommunication;
import general.communication.utils.Address;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple client to send StartMsg to the supervisor for testing purposes.
 * This simulates what the UI would send to start the network.
 */
public class StartNetworkClient {

    public static void main(String[] args) {
        // Configuration - adjust these values as needed
        String supervisorHost = "127.0.0.1";
        int supervisorPort = 7000;
        
        // UI address (where supervisor should send responses) - different IP from supervisor
        String uiHost = "127.0.0.2";  // Different IP from supervisor
        int uiPort = 8000;
        
        // Network configuration
        int numberOfNodes = 1000;           // Number of nodes in the network
        int numberOfSourceNodes = 1;      // Number of nodes that will be sources
        String topology = "full mesh";     // Topology type: "full mesh", "ring", "star", etc.
        String protocol = "blind_coin"; // Protocol type: "anti_entropy", "blind_coin", "feedback_coin"
        String mode = "push";              // Node mode: "push", "pull", "pushpull"
        
        // Parse command line arguments if provided
        // Usage: [supervisorHost] [supervisorPort] [uiHost] [uiPort] [numNodes] [numSources] [topology] [protocol] [mode]
        if (args.length >= 1) {
            supervisorHost = args[0];
        }
        if (args.length >= 2) {
            supervisorPort = Integer.parseInt(args[1]);
        }
        if (args.length >= 3) {
            uiHost = args[2];
        }
        if (args.length >= 4) {
            uiPort = Integer.parseInt(args[3]);
        }
        if (args.length >= 5) {
            numberOfNodes = Integer.parseInt(args[4]);
        }
        if (args.length >= 6) {
            numberOfSourceNodes = Integer.parseInt(args[5]);
        }
        if (args.length >= 7) {
            topology = args[6];
        }
        if (args.length >= 8) {
            protocol = args[7];
        }
        if (args.length >= 9) {
            mode = args[8];
        }
        
        try {
            // Create StartMsg JSON
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> startMsgMap = new HashMap<>();
            startMsgMap.put("direction", "ui_to_supervisor");
            startMsgMap.put("messageType", "start_system");
            startMsgMap.put("addr", uiHost + ":" + uiPort);
            startMsgMap.put("N", numberOfNodes);
            startMsgMap.put("sourceNodes", numberOfSourceNodes);
            startMsgMap.put("topology", topology);
            startMsgMap.put("protocol", protocol);
            startMsgMap.put("mode", mode);
            
            String jsonMessage = objectMapper.writeValueAsString(startMsgMap);
            
            System.out.println("================================================");
            System.out.println("Sending StartMsg to supervisor:");
            System.out.println("  Supervisor Address: " + supervisorHost + ":" + supervisorPort);
            System.out.println("  UI Address (for responses): " + uiHost + ":" + uiPort);
            System.out.println("  Number of Nodes: " + numberOfNodes);
            System.out.println("  Source Nodes: " + numberOfSourceNodes);
            System.out.println("  Topology: " + topology);
            System.out.println("  Protocol: " + protocol);
            System.out.println("  Mode: " + mode);
            System.out.println("  JSON Message: " + jsonMessage);
            System.out.println("================================================");
            
            // Send message via UDP
            // Create a temporary socket for sending (client doesn't need to listen)
            Communication communication = new UdpCommunication();
            // Setup socket with a temporary address (any available port on localhost)
            // This is needed because sendMessage() checks if socket is ready
            Address tempClientAddress = new Address("127.0.0.1", 0); // Port 0 = any available port
            communication.setupSocket(tempClientAddress);
            
            Address supervisorAddress = new Address(supervisorHost, supervisorPort);
            communication.sendMessage(supervisorAddress, jsonMessage);
            
            // Close the temporary socket
            communication.closeSocket();
            
            System.out.println("StartMsg sent successfully!");
            System.out.println("The supervisor should now initialize the network.");
            
        } catch (Exception e) {
            System.err.println("Error sending StartMsg: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
