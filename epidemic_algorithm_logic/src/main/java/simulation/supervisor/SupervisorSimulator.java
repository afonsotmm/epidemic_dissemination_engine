package simulation.supervisor;

import javax.swing.SwingUtilities;
import javax.swing.JFrame;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * Simulator to run the epidemic dissemination system with a supervisor.
 * 
 * Usage example:
 * - Creates N nodes
 * - Each node runs in its own thread
 * - Supervisor monitors infections
 * - Complete graph topology (all nodes connected)
 * 
 * NOTE: This is the OLD simulation supervisor.
 * The NEW supervisor is in: supervisor.Supervisor
 * 
 * To run this OLD simulation supervisor explicitly, use:
 * java -cp <classpath> simulation.supervisor.SupervisorSimulator simulation
 */
public class SupervisorSimulator {

    /**
     * Main entry point - redirects to new supervisor unless explicitly called with
     * "simulation" argument
     */
    public static void main(String[] args) {
        // If explicitly called with "simulation" argument, run the old simulation
        if (args.length > 0 && "simulation".equals(args[0])) {
            simulationMain(args);
            return;
        }

        // Otherwise, inform user to use the new supervisor
        System.out.println("================================================");
        System.out.println("This is the OLD simulation supervisor.");
        System.out.println("The NEW supervisor is: supervisor.Supervisor");
        System.out.println("");
        System.out.println("To run the NEW supervisor:");
        System.out.println("  java supervisor.Supervisor [port]");
        System.out.println("");
        System.out.println("To run this OLD simulation supervisor:");
        System.out.println("  java simulation.supervisor.SupervisorSimulator simulation");
        System.out.println("================================================");
        System.exit(0);
    }

    /**
     * Actual simulation main method - only called when explicitly requested
     */
    public static void simulationMain(String[] args) {
        // Configuration
        int numberOfNodes = 1000; // Number of nodes to create
        int nodePort = 5000; // Port for all nodes (same port, different IPs: 127.0.0.1, 127.0.0.2, ...,
                             // 127.0.X.0)
        int supervisorPort = 6000; // Supervisor port
        double roundInterval = 1.0; // Round interval in seconds
        Supervisor.NodeMode nodeMode = Supervisor.NodeMode.FEEDBACK_COIN_PUSHPULL; // PULL, PUSH, PUSHPULL,
                                                                                   // BLIND_COIN_PUSH, BLIND_COIN_PULL,
                                                                                   // BLIND_COIN_PUSHPULL,
                                                                                   // FEEDBACK_COIN_PUSH,
                                                                                   // FEEDBACK_COIN_PULL,
                                                                                   // FEEDBACK_COIN_PUSHPULL
        double blindCoinK = 2; // Probability parameter for Blind Coin and Feedback Coin (1/k chance to stop
                               // spreading). Used for BLIND_COIN_* and FEEDBACK_COIN_* modes

        // Create supervisor
        Supervisor supervisor;
        if (nodeMode == Supervisor.NodeMode.BLIND_COIN_PUSH ||
                nodeMode == Supervisor.NodeMode.BLIND_COIN_PULL ||
                nodeMode == Supervisor.NodeMode.BLIND_COIN_PUSHPULL ||
                nodeMode == Supervisor.NodeMode.FEEDBACK_COIN_PUSH ||
                nodeMode == Supervisor.NodeMode.FEEDBACK_COIN_PULL ||
                nodeMode == Supervisor.NodeMode.FEEDBACK_COIN_PUSHPULL) {
            supervisor = new Supervisor(numberOfNodes, nodePort, supervisorPort, nodeMode, blindCoinK);
        } else {
            supervisor = new Supervisor(numberOfNodes, nodePort, supervisorPort, nodeMode);
        }

        // Initialize supervisor (starts listening)
        supervisor.initialize();

        // Define which nodes are sources and for which subjects
        // 3 nodes are sources with different subjects
        Map<Integer, String> subjectsForNodes = new HashMap<>();
        subjectsForNodes.put(1, "temperature");
        // Other nodes are not sources (will be null)

        // Create and start all nodes
        supervisor.createAndStartNodes(subjectsForNodes);

        // Start monitoring for INFECTED messages
        supervisor.startMonitoring();

        // Start automatic round scheduling
        supervisor.startRoundScheduling(roundInterval);

        // Create and show GUI
        SwingUtilities.invokeLater(() -> {
            NodeGui gui = new NodeGui(supervisor);
            gui.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            gui.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                    gui.stop();
                    gui.dispose();
                }
            });
        });

        System.out.println("\n=== Epidemic Dissemination System Started (" + nodeMode + " Mode) ===");
        System.out.println("Supervisor monitoring " + numberOfNodes + " nodes");
        System.out.println("Round interval: " + roundInterval + " seconds");
        System.out.println("GUI opened - Click on nodes to view their messages");
        System.out.println("Commands:");
        System.out.println("  Enter - Print infection statistics");
        System.out.println("  'state' - Print current state of all nodes (subjects and values)");
        System.out.println("  'chart' - Generate and display infection chart for all subjects and sources");
        System.out.println(
                "  'chart <subject>' - Generate and display infection chart for specific subject (all sources)");
        System.out.println(
                "  'chart <subject>:<sourceId>' - Generate and display infection chart for specific subject+source");
        System.out.println("  'subjects' - List all available subject:sourceId combinations");
        System.out.println("  'round' - Manually trigger a round");
        System.out.println("  'q' - Quit\n");

        // Interactive loop
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("q")) {
                break;
            } else if (input.equalsIgnoreCase("state")) {
                supervisor.printNodesState();
            } else if (input.equalsIgnoreCase("chart")) {
                supervisor.generateInfectionChart();
            } else if (input.toLowerCase().startsWith("chart ")) {
                String filter = input.substring(6).trim();
                if (!filter.isEmpty()) {
                    // Check if it's in format "subject:sourceId"
                    if (filter.contains(":")) {
                        String[] parts = filter.split(":", 2);
                        if (parts.length == 2) {
                            try {
                                String subject = parts[0].trim();
                                Integer sourceId = Integer.parseInt(parts[1].trim());
                                supervisor.generateInfectionChart(subject, sourceId);
                            } catch (NumberFormatException e) {
                                System.out.println("Invalid sourceId format. Use: chart <subject>:<sourceId>");
                            }
                        } else {
                            System.out.println("Invalid format. Use: chart <subject>:<sourceId>");
                        }
                    } else {
                        // Just subject, all sources
                        supervisor.generateInfectionChart(filter, null);
                    }
                } else {
                    supervisor.generateInfectionChart();
                }
            } else if (input.equalsIgnoreCase("subjects")) {
                java.util.Set<String> combinations = supervisor.getAllSubjectSourceCombinations();
                if (combinations.isEmpty()) {
                    System.out.println(
                            "No subject:sourceId combinations found yet. Wait for nodes to generate messages.");
                } else {
                    System.out.println("Available subject:sourceId combinations:");
                    for (String combo : combinations) {
                        System.out.println("  - " + combo);
                    }
                }
            } else if (input.equalsIgnoreCase("round")) {
                supervisor.triggerPullRound();
            } else if (input.isEmpty()) {
                supervisor.printInfectionStatistics();
            }
        }

        // Shutdown
        supervisor.shutdown();
        scanner.close();
        System.out.println("Simulation ended.");
    }
}
