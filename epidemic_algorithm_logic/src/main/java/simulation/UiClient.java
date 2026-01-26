package simulation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import epidemic_core.message.supervisor_to_ui.infection_update.InfectionUpdateMsg;
import epidemic_core.message.supervisor_to_ui.structural_infos.StructuralInfosMsg;
import general.communication.implementation.TcpCommunication;
import general.communication.utils.Address;

import javax.swing.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * External UI client that sends commands to supervisor and receives network visualization data.
 * Acts as a TCP server to receive messages from the supervisor.
 * 
 * Usage:
 *   java simulation.UiClient [supervisorHost] [supervisorPort] [uiPort]
 *   
 * Then type "start" or "end" in the terminal to send messages.
 */
public class UiClient {
    private static String supervisorHost = "127.0.0.1";
    private static int supervisorPort = 7000;
    private static String uiHost = "127.0.0.2";
    private static int uiPort = 8000;
    private static int numberOfNodes = 300;
    private static int numberOfSourceNodes = 1;
    private static String topology = "full mesh";
    private static String protocol = "anti_entropy";
    private static String mode = "push";
    private static String deployment = "local"; // "local" or "distributed"

    private static NetworkGraphGui graphGui;
    private static TcpCommunication tcpServer;
    private static volatile boolean isRunning = false;

    public static void main(String[] args) {
        if (args.length >= 1) {
            supervisorHost = args[0];
        }
        if (args.length >= 2) {
            supervisorPort = Integer.parseInt(args[1]);
        }
        if (args.length >= 3) {
            uiPort = Integer.parseInt(args[2]);
        }

        initializeTcpServer();

        Thread.startVirtualThread(UiClient::processMessages);

        graphGui = new NetworkGraphGui();

        graphGui.setStartCallback(UiClient::sendStartMessage);
        graphGui.setEndCallback(UiClient::sendEndMessage);

        graphGui.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        graphGui.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                isRunning = false;
                if (tcpServer != null) {
                    tcpServer.closeSocket();
                }
                System.exit(0);
            }
        });
        
        graphGui.setVisible(true);
        
        System.out.println("================================================");
        System.out.println("UI Client - Running");
        System.out.println("================================================");
        System.out.println("Supervisor Address: " + supervisorHost + ":" + supervisorPort);
        System.out.println("UI Server listening on: " + uiHost + ":" + uiPort);
        System.out.println("Use the GUI buttons to start/end the network");
        System.out.println("================================================");
        System.out.println();

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void initializeTcpServer() {
        isRunning = true;
        
        try {
            tcpServer = new TcpCommunication();
            Address uiAddress = new Address(uiHost, uiPort);
            tcpServer.setupSocket(uiAddress);
            System.out.println("UI TCP server initialized on " + uiHost + ":" + uiPort);
        } catch (Exception e) {
            System.err.println("Error initializing TCP server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void processMessages() {
        while (isRunning) {
            try {
                String message = tcpServer.receiveMessage();
                if (message != null) {
                    processSupervisorMessage(message);
                }
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Error processing message: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static void processSupervisorMessage(String jsonMessage) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(jsonMessage);
            
            String direction = jsonNode.has("direction") ? jsonNode.get("direction").asText() : null;
            String messageType = jsonNode.has("messageType") ? jsonNode.get("messageType").asText() : null;
            
            if ("supervisor_to_ui".equals(direction)) {
                if ("structural_infos".equals(messageType)) {
                    StructuralInfosMsg structuralMsg = StructuralInfosMsg.decodeMessage(jsonMessage);
                    if (graphGui != null) {
                        graphGui.updateTopology(structuralMsg);
                        System.out.println("Received structural information: " + structuralMsg.getNodes().size() + " nodes");
                    }
                } else if ("infection_update".equals(messageType)) {
                    InfectionUpdateMsg infectionMsg = InfectionUpdateMsg.decodeMessage(jsonMessage);
                    if (graphGui != null) {
                        int updatedNodeId = infectionMsg.getUpdatedNodeId();
                        int infectingNodeId = infectionMsg.getInfectingNodeId();
                        int sourceId = infectionMsg.getSourceId() != null ? infectionMsg.getSourceId() : -1;

                        graphGui.updateNodeInfection(updatedNodeId, true);

                        graphGui.flashEdge(infectingNodeId, updatedNodeId);
                        
                        System.out.println("Node " + updatedNodeId + " infected by node " + infectingNodeId + 
                                         " (source: " + sourceId + ")");
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error decoding supervisor message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void sendStartMessage() {
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
            startMsgMap.put("deployment", deployment);
            
            String jsonMessage;
            try {
                jsonMessage = objectMapper.writeValueAsString(startMsgMap);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Error serializing StartMsg to JSON", e);
            }
            
            System.out.println("================================================");
            System.out.println("Sending StartMsg to supervisor:");
            System.out.println("  Supervisor Address: " + supervisorHost + ":" + supervisorPort);
            System.out.println("  UI Address (for responses): " + uiHost + ":" + uiPort);
            System.out.println("  Number of Nodes: " + numberOfNodes);
            System.out.println("  Source Nodes: " + numberOfSourceNodes);
            System.out.println("  Topology: " + topology);
            System.out.println("  Protocol: " + protocol);
            System.out.println("  Mode: " + mode);
            System.out.println("  Deployment: " + deployment);
            System.out.println("================================================");
            
            // Send message via TCP
            TcpCommunication communication = new TcpCommunication();
            Address supervisorAddress = new Address(supervisorHost, supervisorPort);
            communication.sendMessage(supervisorAddress, jsonMessage);
            
            System.out.println("✓ StartMsg sent successfully via TCP!");
            System.out.println("  The supervisor should now initialize the network.");
            System.out.println();
            
        } catch (Exception e) {
            System.err.println("Error creating/sending StartMsg: " + e.getMessage());
            throw e;
        }
    }

    private static void sendEndMessage() {
        try {
            // Create EndMsg JSON
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> endMsgMap = new HashMap<>();
            endMsgMap.put("direction", "ui_to_supervisor");
            endMsgMap.put("messageType", "end_system");
            
            String jsonMessage;
            try {
                jsonMessage = objectMapper.writeValueAsString(endMsgMap);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Error serializing EndMsg to JSON", e);
            }
            
            System.out.println("================================================");
            System.out.println("Sending EndMsg to supervisor:");
            System.out.println("  Supervisor Address: " + supervisorHost + ":" + supervisorPort);
            System.out.println("  JSON Message: " + jsonMessage);
            System.out.println("================================================");
            
            // Send message via TCP
            TcpCommunication communication = new TcpCommunication();
            Address supervisorAddress = new Address(supervisorHost, supervisorPort);
            communication.sendMessage(supervisorAddress, jsonMessage);
            
            System.out.println("✓ EndMsg sent successfully via TCP!");
            System.out.println("  The supervisor should now stop the network.");
            System.out.println();
            
        } catch (Exception e) {
            System.err.println("Error creating/sending EndMsg: " + e.getMessage());
            throw e;
        }
    }
}
