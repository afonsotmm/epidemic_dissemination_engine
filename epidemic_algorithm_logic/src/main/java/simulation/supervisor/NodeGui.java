package simulation.supervisor;

import simulation.supervisor.Supervisor.InfectionRecord;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Graphical interface to visualize nodes and their stored messages.
 * Allows clicking on nodes to see their stored messages.
 */
public class NodeGui extends JFrame {
    
    private final Supervisor supervisor;
    private JPanel nodesPanel;
    private JTextArea messagesArea;
    private JLabel selectedNodeLabel;
    private Timer refreshTimer;
    
    public NodeGui(Supervisor supervisor) {
        this.supervisor = supervisor;
        initializeGui();
        startAutoRefresh();
    }
    
    private void initializeGui() {
        setTitle("Epidemic Dissemination - Node Monitor");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        
        // Top panel with title
        JPanel topPanel = new JPanel();
        topPanel.setBorder(new TitledBorder("Node Selection"));
        topPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        selectedNodeLabel = new JLabel("Click on a node to view its messages");
        topPanel.add(selectedNodeLabel);
        add(topPanel, BorderLayout.NORTH);
        
        // Center panel with nodes grid
        nodesPanel = new JPanel();
        nodesPanel.setLayout(new GridLayout(0, 10, 5, 5)); // 10 columns, auto rows
        nodesPanel.setBorder(new TitledBorder("Nodes"));
        
        JScrollPane nodesScrollPane = new JScrollPane(nodesPanel);
        nodesScrollPane.setPreferredSize(new Dimension(800, 200));
        add(nodesScrollPane, BorderLayout.CENTER);
        
        // Bottom panel with messages
        JPanel messagesPanel = new JPanel();
        messagesPanel.setLayout(new BorderLayout());
        messagesPanel.setBorder(new TitledBorder("Stored Messages"));
        
        messagesArea = new JTextArea();
        messagesArea.setEditable(false);
        messagesArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane messagesScrollPane = new JScrollPane(messagesArea);
        messagesScrollPane.setPreferredSize(new Dimension(800, 300));
        messagesPanel.add(messagesScrollPane, BorderLayout.CENTER);
        
        // Refresh button
        JButton refreshButton = new JButton("Refresh Now");
        refreshButton.addActionListener(e -> refreshNodes());
        messagesPanel.add(refreshButton, BorderLayout.SOUTH);
        
        add(messagesPanel, BorderLayout.SOUTH);
        
        // Initial refresh
        refreshNodes();
        
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }
    
    private void refreshNodes() {
        nodesPanel.removeAll();
        
        List<Integer> nodeIds = supervisor.getAllNodeIds();
        nodeIds.sort(Integer::compareTo);
        
        for (Integer nodeId : nodeIds) {
            JButton nodeButton = createNodeButton(nodeId);
            nodesPanel.add(nodeButton);
        }
        
        nodesPanel.revalidate();
        nodesPanel.repaint();
    }
    
    private JButton createNodeButton(int nodeId) {
        JButton button = new JButton("Node " + nodeId);
        
        // Get infection status from supervisor (based on messages received)
        List<InfectionRecord> infections = supervisor.getNodeInfections(nodeId);
        boolean isInfected = !infections.isEmpty();
        boolean isSource = supervisor.isNodeSource(nodeId);
        boolean hasRemovedMessages = supervisor.hasRemovedMessages(nodeId);
        
        // Color coding based on status
        // Priority: REMOVED (red) > SOURCE (green) > INFECTED (blue) > SUSCEPTIBLE (gray)
        if (hasRemovedMessages) {
            // Node with removed messages (Gossip protocol) - red
            int removedCount = supervisor.getRemovedMessagesCount(nodeId);
            button.setBackground(new Color(255, 100, 100)); // Light red
            button.setToolTipText("REMOVED (" + removedCount + " message(s) removed)");
        } else if (!isInfected) {
            // Susceptible (not infected yet) - gray
            button.setBackground(new Color(200, 200, 200)); // Light gray
            button.setToolTipText("SUSCEPTIBLE (not infected)");
        } else if (isSource) {
            // Source node - green
            button.setBackground(new Color(144, 238, 144)); // Light green
            button.setToolTipText("SOURCE: " + supervisor.getSourceSubject(nodeId));
        } else {
            // Infected forwarder - blue
            button.setBackground(new Color(173, 216, 230)); // Light blue
            button.setToolTipText("INFECTED (FORWARDER)");
        }
        
        // Get unique subject+sourceId combinations count
        long uniqueMessages = infections.stream()
            .map(inf -> inf.getSubject() + ":" + inf.getSourceId())
            .distinct()
            .count();
        
        if (uniqueMessages > 0) {
            button.setText("Node " + nodeId + " (" + uniqueMessages + ")");
        }
        
        button.addActionListener(e -> showNodeMessages(nodeId));
        
        return button;
    }
    
    private void showNodeMessages(int nodeId) {
        List<InfectionRecord> infections = supervisor.getNodeInfections(nodeId);
        boolean isSource = supervisor.isNodeSource(nodeId);
        
        selectedNodeLabel.setText("Node " + nodeId + 
            (isSource ? " (SOURCE: " + supervisor.getSourceSubject(nodeId) + ")" : 
             infections.isEmpty() ? " (SUSCEPTIBLE)" : " (INFECTED)"));
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== Node ").append(nodeId).append(" Status ===\n");
        sb.append("Status: ");
        if (infections.isEmpty()) {
            sb.append("SUSCEPTIBLE (not infected yet)\n");
        } else if (isSource) {
            sb.append("SOURCE (").append(supervisor.getSourceSubject(nodeId)).append(")\n");
        } else {
            sb.append("INFECTED (FORWARDER)\n");
        }
        sb.append("Total unique messages: ").append(
            infections.stream()
                .map(inf -> inf.getSubject() + ":" + inf.getSourceId())
                .distinct()
                .count()
        ).append("\n\n");
        
        if (infections.isEmpty()) {
            sb.append("No infections recorded yet.\n");
        } else {
            sb.append(String.format("%-15s %-12s %-10s %-10s\n", 
                "Subject", "Timestamp", "SourceId", "Round"));
            sb.append("------------------------------------------------------------\n");
            
            // Group by subject+sourceId and show most recent
            Map<String, InfectionRecord> latestBySubjectSource = infections.stream()
                .collect(Collectors.toMap(
                    inf -> inf.getSubject() + ":" + inf.getSourceId(),
                    inf -> inf,
                    (inf1, inf2) -> inf1.getTimestamp() > inf2.getTimestamp() ? inf1 : inf2
                ));
            
            for (InfectionRecord record : latestBySubjectSource.values()) {
                sb.append(String.format("%-15s %-12d %-10d %-10d\n",
                    record.getSubject(),
                    record.getTimestamp(),
                    record.getSourceId(),
                    record.getRound()));
            }
        }
        
        messagesArea.setText(sb.toString());
    }
    
    private void startAutoRefresh() {
        // Refresh every 2 seconds
        refreshTimer = new Timer(2000, e -> refreshNodes());
        refreshTimer.start();
    }
    
    public void stop() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
    }
}

