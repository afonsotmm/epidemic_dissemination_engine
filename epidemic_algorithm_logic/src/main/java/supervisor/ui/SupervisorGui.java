package supervisor.ui;

import epidemic_core.message.common.MessageId;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.data.category.DefaultCategoryDataset;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Graphical interface for the supervisor to visualize nodes and infections.
 * Similar to the old simulation supervisor's UI.
 */
public class SupervisorGui {
    
    // Infection tracking
    private final Map<Integer, List<InfectionRecord>> infectionHistory;
    private final Map<Integer, Set<MessageId>> remotionHistory;
    private final Map<Integer, String> sourceNodes; // nodeId -> subject
    
    // Round tracking
    private int currentRound = 0;
    private final Map<Integer, Set<Integer>> infectionsPerRound;
    private final Set<Integer> uniqueInfectedNodes;
    
    // Chart components
    private ChartFrame chartFrame;
    private volatile boolean chartAutoUpdate = false;
    private java.util.Timer chartUpdateTimer;
    
    // Node GUI components
    private JFrame nodeGuiFrame;
    private JPanel nodesPanel;
    private JTextArea messagesArea;
    private JLabel selectedNodeLabel;
    private javax.swing.Timer refreshTimer;
    
    // Discovered nodes table
    private JTable discoveredNodesTable;
    private DefaultTableModel discoveredNodesTableModel;
    private JLabel discoveredNodesStatusLabel;
    
    // Discovered nodes tracking
    private static class DiscoveredNodeInfo {
        final int nodeId;
        final String tcpAddress;
        final String udpAddress;
        final LocalDateTime discoveryTime;
        
        DiscoveredNodeInfo(int nodeId, String tcpAddress, String udpAddress) {
            this.nodeId = nodeId;
            this.tcpAddress = tcpAddress;
            this.udpAddress = udpAddress;
            this.discoveryTime = LocalDateTime.now();
        }
    }
    
    private final Map<Integer, DiscoveredNodeInfo> discoveredNodes; // nodeId -> node info
    private LocalDateTime searchingStartTime;
    
    // Network info
    private final int numberOfNodes;
    
    /**
     * Record to store infection information
     */
    public static class InfectionRecord {
        private final int nodeId;
        private final String subject;
        private final int timestamp;
        private final int sourceId;
        private final LocalDateTime infectionTime;
        private final int round;
        private final String data;
        
        public InfectionRecord(int nodeId, String subject, int timestamp, int sourceId, int round, String data) {
            this.nodeId = nodeId;
            this.subject = subject;
            this.timestamp = timestamp;
            this.sourceId = sourceId;
            this.infectionTime = LocalDateTime.now();
            this.round = round;
            this.data = data;
        }
        
        public int getNodeId() { return nodeId; }
        public String getSubject() { return subject; }
        public int getTimestamp() { return timestamp; }
        public int getSourceId() { return sourceId; }
        public LocalDateTime getInfectionTime() { return infectionTime; }
        public int getRound() { return round; }
        public String getData() { return data; }
        
        @Override
        public String toString() {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return String.format("Node %d infected with subject '%s' from source %d (timestamp: %d, data: %s) at round %d - %s", 
                    nodeId, subject, sourceId, timestamp, data, round, infectionTime.format(formatter));
        }
    }
    
    public SupervisorGui(int numberOfNodes) {
        this.numberOfNodes = numberOfNodes;
        this.infectionHistory = new ConcurrentHashMap<>();
        this.remotionHistory = new ConcurrentHashMap<>();
        this.sourceNodes = new ConcurrentHashMap<>();
        this.infectionsPerRound = new ConcurrentHashMap<>();
        this.uniqueInfectedNodes = ConcurrentHashMap.newKeySet();
        this.discoveredNodes = new ConcurrentHashMap<>();
        this.searchingStartTime = null;
        
        // Initialize infection history for all nodes
        for (int i = 0; i < numberOfNodes; i++) {
            infectionHistory.put(i, Collections.synchronizedList(new ArrayList<>()));
            remotionHistory.put(i, ConcurrentHashMap.newKeySet());
        }
        
        // Initialize round 0
        infectionsPerRound.put(0, ConcurrentHashMap.newKeySet());
        
        // Initialize GUI
        SwingUtilities.invokeLater(this::initializeGui);
    }
    
    private void initializeGui() {
        // Create main frame with tabs
        JFrame mainFrame = new JFrame("Epidemic Dissemination Supervisor");
        mainFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        mainFrame.setLayout(new BorderLayout());
        
        JTabbedPane tabbedPane = new JTabbedPane();
        
        // Chart tab
        JPanel chartPanel = new JPanel(new BorderLayout());
        JButton showChartButton = new JButton("Show Infection Chart");
        showChartButton.addActionListener(e -> generateInfectionChart());
        chartPanel.add(showChartButton, BorderLayout.NORTH);
        tabbedPane.addTab("Charts", chartPanel);
        
        // Discovered Nodes tab
        JPanel discoveredNodesPanel = initializeDiscoveredNodesPanel();
        tabbedPane.addTab("Discovered Nodes", discoveredNodesPanel);
        
        // Nodes tab
        initializeNodeGui();
        tabbedPane.addTab("Nodes", nodeGuiFrame.getContentPane());
        
        mainFrame.add(tabbedPane, BorderLayout.CENTER);
        mainFrame.pack();
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setVisible(true);
    }
    
    private void initializeNodeGui() {
        nodeGuiFrame = new JFrame();
        nodeGuiFrame.setTitle("Node Monitor");
        nodeGuiFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        nodeGuiFrame.setLayout(new BorderLayout());
        
        // Top panel with title
        JPanel topPanel = new JPanel();
        topPanel.setBorder(new TitledBorder("Node Selection"));
        topPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        selectedNodeLabel = new JLabel("Click on a node to view its messages");
        topPanel.add(selectedNodeLabel);
        nodeGuiFrame.add(topPanel, BorderLayout.NORTH);
        
        // Center panel with nodes grid
        nodesPanel = new JPanel();
        nodesPanel.setLayout(new GridLayout(0, 10, 5, 5)); // 10 columns, auto rows
        nodesPanel.setBorder(new TitledBorder("Nodes"));
        
        JScrollPane nodesScrollPane = new JScrollPane(nodesPanel);
        nodesScrollPane.setPreferredSize(new Dimension(800, 200));
        nodeGuiFrame.add(nodesScrollPane, BorderLayout.CENTER);
        
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
        
        nodeGuiFrame.add(messagesPanel, BorderLayout.SOUTH);
        
        // Initial refresh
        refreshNodes();
        
        // Auto-refresh every 2 seconds
        refreshTimer = new javax.swing.Timer(2000, e -> refreshNodes());
        refreshTimer.start();
        
        nodeGuiFrame.pack();
        nodeGuiFrame.setLocationRelativeTo(null);
    }
    
    /**
     * Initialize discovered nodes panel with table
     */
    private JPanel initializeDiscoveredNodesPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Nodes Discovered During SEARCHING Mode"));
        
        // Create table model with more columns
        String[] columnNames = {"Node ID", "TCP Address", "UDP Address", "Discovery Time"};
        discoveredNodesTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make table read-only
            }
            
            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 0) return Integer.class; // Node ID
                return String.class; // Addresses and Time
            }
        };
        
        // Create table
        discoveredNodesTable = new JTable(discoveredNodesTableModel);
        discoveredNodesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        discoveredNodesTable.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        discoveredNodesTable.getTableHeader().setReorderingAllowed(false);
        discoveredNodesTable.setRowHeight(20);
        
        // Set column widths
        discoveredNodesTable.getColumnModel().getColumn(0).setPreferredWidth(60);   // Node ID
        discoveredNodesTable.getColumnModel().getColumn(1).setPreferredWidth(180);  // TCP Address
        discoveredNodesTable.getColumnModel().getColumn(2).setPreferredWidth(180);  // UDP Address
        discoveredNodesTable.getColumnModel().getColumn(3).setPreferredWidth(160);  // Discovery Time
        
        // Add table to scroll pane
        JScrollPane scrollPane = new JScrollPane(discoveredNodesTable);
        scrollPane.setPreferredSize(new Dimension(650, 350));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Status panel with more information
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        discoveredNodesStatusLabel = new JLabel("No nodes discovered yet");
        discoveredNodesStatusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        statusPanel.add(discoveredNodesStatusLabel, BorderLayout.CENTER);
        
        // Auto-refresh status label every second to update elapsed time
        javax.swing.Timer statusTimer = new javax.swing.Timer(1000, e -> updateDiscoveredNodesStatus());
        statusTimer.start();
        
        panel.add(statusPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * Record a discovered node (called by Supervisor when HelloMsg is received)
     */
    public void recordDiscoveredNode(int nodeId, String tcpAddress, String udpAddress) {
        if (searchingStartTime == null) {
            searchingStartTime = LocalDateTime.now();
        }
        discoveredNodes.put(nodeId, new DiscoveredNodeInfo(nodeId, tcpAddress, udpAddress));
        SwingUtilities.invokeLater(() -> updateDiscoveredNodesTable());
    }
    
    /**
     * Overloaded method for backward compatibility (only TCP address)
     */
    public void recordDiscoveredNode(int nodeId, String tcpAddress) {
        recordDiscoveredNode(nodeId, tcpAddress, "N/A");
    }
    
    /**
     * Update the discovered nodes table with current data
     */
    private void updateDiscoveredNodesTable() {
        if (discoveredNodesTableModel == null) {
            return; // Table not initialized yet
        }
        
        // Clear existing rows
        discoveredNodesTableModel.setRowCount(0);
        
        // Add discovered nodes sorted by node ID
        List<Integer> sortedNodeIds = new ArrayList<>(discoveredNodes.keySet());
        Collections.sort(sortedNodeIds);
        
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        
        for (Integer nodeId : sortedNodeIds) {
            DiscoveredNodeInfo info = discoveredNodes.get(nodeId);
            if (info != null) {
                String discoveryTimeStr = info.discoveryTime.format(timeFormatter);
                discoveredNodesTableModel.addRow(new Object[]{
                    info.nodeId,
                    info.tcpAddress,
                    info.udpAddress,
                    discoveryTimeStr
                });
            }
        }
        
        updateDiscoveredNodesStatus();
    }
    
    /**
     * Update the status label with progress and elapsed time
     */
    private void updateDiscoveredNodesStatus() {
        if (discoveredNodesStatusLabel == null) {
            return;
        }
        
        int count = discoveredNodes.size();
        StringBuilder status = new StringBuilder();
        
        status.append("<html>");
        status.append("<b>Status:</b> ");
        
        if (count == 0) {
            status.append("No nodes discovered yet");
            if (searchingStartTime != null) {
                long elapsedSeconds = java.time.Duration.between(searchingStartTime, LocalDateTime.now()).getSeconds();
                status.append(" | Elapsed: ").append(formatDuration(elapsedSeconds));
            }
        } else {
            status.append(count).append(" node(s) discovered");
            
            if (numberOfNodes > 0) {
                double percentage = (count * 100.0) / numberOfNodes;
                status.append(" (").append(count).append("/").append(numberOfNodes).append(" - ");
                status.append(String.format("%.1f", percentage)).append("%)");
                
                // Add progress indicator
                int progressBars = (int) (percentage / 5); // 5% per bar
                status.append(" [");
                for (int i = 0; i < 20; i++) {
                    status.append(i < progressBars ? "█" : "░");
                }
                status.append("]");
            }
            
            if (searchingStartTime != null) {
                long elapsedSeconds = java.time.Duration.between(searchingStartTime, LocalDateTime.now()).getSeconds();
                status.append(" | Elapsed: ").append(formatDuration(elapsedSeconds));
                
                // Show discovery rate if more than one node
                if (count > 1 && elapsedSeconds > 0) {
                    double rate = count / (double) elapsedSeconds;
                    status.append(" | Rate: ").append(String.format("%.2f", rate)).append(" nodes/sec");
                }
                
                // Show estimated time to complete (if searching is active)
                if (numberOfNodes > count && count > 0) {
                    long avgTimePerNode = elapsedSeconds / count;
                    long remainingNodes = numberOfNodes - count;
                    long estimatedSeconds = avgTimePerNode * remainingNodes;
                    if (estimatedSeconds > 0 && estimatedSeconds < 300) { // Only show if less than 5 minutes
                        status.append(" | ETA: ").append(formatDuration(estimatedSeconds));
                    }
                }
            }
        }
        
        status.append("</html>");
        discoveredNodesStatusLabel.setText(status.toString());
    }
    
    /**
     * Format duration in seconds to human-readable format
     */
    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            long mins = seconds / 60;
            long secs = seconds % 60;
            return mins + "m " + secs + "s";
        } else {
            long hours = seconds / 3600;
            long mins = (seconds % 3600) / 60;
            long secs = seconds % 60;
            return hours + "h " + mins + "m " + secs + "s";
        }
    }
    
    /**
     * Record an infection update
     */
    public void recordInfection(int nodeId, int infectingNodeId, String subject, int sourceId, int timestamp, String data) {
        recordInfection(nodeId, infectingNodeId, subject, sourceId, timestamp, data, currentRound);
    }
    
    /**
     * Record an infection update with explicit round
     */
    public void recordInfection(int nodeId, int infectingNodeId, String subject, int sourceId, int timestamp, String data, int round) {
        System.out.println("[SupervisorGui] recordInfection() called: nodeId=" + nodeId + 
                         ", infectingNodeId=" + infectingNodeId + 
                         ", subject=" + subject + 
                         ", sourceId=" + sourceId + 
                         ", timestamp=" + timestamp + 
                         ", round=" + round);
        
        // Track source nodes - a node is a source if it generated the message (nodeId == sourceId)
        if (nodeId == sourceId) {
            sourceNodes.put(nodeId, subject);
            System.out.println("[SupervisorGui] Detected SOURCE node: " + nodeId + " with subject: " + subject);
        }
        
        // Add to infection history
        List<InfectionRecord> records = infectionHistory.get(nodeId);
        if (records == null) {
            System.err.println("[SupervisorGui] ERROR: No infection history for nodeId=" + nodeId + 
                             " (infectionHistory size=" + infectionHistory.size() + 
                             ", numberOfNodes=" + numberOfNodes + ")");
            // Try to initialize it if it's a valid node ID
            if (nodeId >= 0 && nodeId < numberOfNodes) {
                System.out.println("[SupervisorGui] Initializing infection history for nodeId=" + nodeId);
                records = Collections.synchronizedList(new ArrayList<>());
                infectionHistory.put(nodeId, records);
            } else {
                System.err.println("[SupervisorGui] ERROR: Invalid nodeId=" + nodeId + " (must be 0-" + (numberOfNodes-1) + ")");
                return;
            }
        }
        
        InfectionRecord record = new InfectionRecord(nodeId, subject, timestamp, sourceId, round, data);
        records.add(record);
        System.out.println("[SupervisorGui] Added InfectionRecord to history for nodeId=" + nodeId + 
                         " (total records for this node: " + records.size() + ")");
        
        // Track infection per round ONLY if this is the first time this node gets infected
        // (ignore updates to already infected nodes to show true growth curve)
        boolean isNewInfection = uniqueInfectedNodes.add(nodeId);
        if (isNewInfection) {
            infectionsPerRound.computeIfAbsent(round, k -> ConcurrentHashMap.newKeySet()).add(nodeId);
            System.out.println("[SupervisorGui] INFECTED (NEW): nodeId=" + nodeId + 
                             ", sourceId=" + sourceId + 
                             ", subject=" + subject + 
                             ", round=" + round + 
                             " (infected by node " + infectingNodeId + ")");
        } else {
            // This is an update to an already infected node - don't count as new infection
            System.out.println("[SupervisorGui] INFECTED (UPDATE): nodeId=" + nodeId + 
                             ", sourceId=" + sourceId + 
                             ", subject=" + subject + 
                             ", round=" + round + 
                             " (infected by node " + infectingNodeId + ")");
        }
    }
    
    /**
     * Record a remotion update
     */
    public void recordRemotion(int nodeId, String subject, int sourceId, int timestamp) {
        MessageId msgId = new MessageId(
            new epidemic_core.message.common.MessageTopic(subject, sourceId),
            timestamp
        );
        remotionHistory.computeIfAbsent(nodeId, k -> ConcurrentHashMap.newKeySet()).add(msgId);
    }
    
    /**
     * Increment round counter
     */
    public void incrementRound() {
        currentRound++;
        infectionsPerRound.put(currentRound, ConcurrentHashMap.newKeySet());
    }
    
    /**
     * Generate and display infection chart
     */
    public void generateInfectionChart() {
        SwingUtilities.invokeLater(() -> {
            DefaultCategoryDataset dataset = new DefaultCategoryDataset();
            
            // Group infections by round (cumulative)
            Map<Integer, Set<Integer>> uniqueInfectionsPerRound = new TreeMap<>();
            Set<Integer> cumulativeInfectedNodes = new HashSet<>();
            
            // Track first infection per node
            Map<Integer, Integer> firstInfectionRound = new HashMap<>();
            
            // First pass: find first infection round for each node
            for (Map.Entry<Integer, List<InfectionRecord>> entry : infectionHistory.entrySet()) {
                int nodeId = entry.getKey();
                for (InfectionRecord record : entry.getValue()) {
                    int round = record.getRound();
                    if (!firstInfectionRound.containsKey(nodeId) || round < firstInfectionRound.get(nodeId)) {
                        firstInfectionRound.put(nodeId, round);
                    }
                }
            }
            
            // Process all rounds up to current round
            for (int round = 0; round <= currentRound; round++) {
                Set<Integer> roundInfections = new HashSet<>();
                
                for (Map.Entry<Integer, Integer> entry : firstInfectionRound.entrySet()) {
                    int nodeId = entry.getKey();
                    int firstRound = entry.getValue();
                    if (firstRound == round) {
                        roundInfections.add(nodeId);
                    }
                }
                
                cumulativeInfectedNodes.addAll(roundInfections);
                uniqueInfectionsPerRound.put(round, new HashSet<>(cumulativeInfectedNodes));
            }
            
            // Add data to dataset
            int maxRound = uniqueInfectionsPerRound.isEmpty() ? 0 : Collections.max(uniqueInfectionsPerRound.keySet());
            for (int round = 0; round <= maxRound; round++) {
                int cumulativeCount = uniqueInfectionsPerRound.getOrDefault(round, Collections.emptySet()).size();
                if (cumulativeCount >= numberOfNodes) {
                    cumulativeCount = numberOfNodes;
                    dataset.addValue(cumulativeCount, "Cumulative Infections", String.valueOf(round));
                    break;
                }
                dataset.addValue(cumulativeCount, "Cumulative Infections", String.valueOf(round));
            }
            
            // Create chart
            JFreeChart chart = ChartFactory.createLineChart(
                    "Infections Over Rounds (All Subjects & Sources)",
                    "Round",
                    "Cumulative Infections",
                    dataset,
                    PlotOrientation.VERTICAL,
                    true,
                    true,
                    false
            );
            
            // Configure Y-axis to show only integers
            CategoryPlot plot = (CategoryPlot) chart.getPlot();
            NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
            rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
            
            // Display chart
            if (chartFrame == null || !chartFrame.isVisible()) {
                chartFrame = new ChartFrame("Infection Chart", chart);
                chartFrame.pack();
                chartFrame.setVisible(true);
                
                // Start auto-update
                if (!chartAutoUpdate) {
                    startChartAutoUpdate();
                }
            } else {
                chartFrame.getChartPanel().setChart(chart);
            }
        });
    }
    
    private void startChartAutoUpdate() {
        chartAutoUpdate = true;
        chartUpdateTimer = new java.util.Timer(true);
        chartUpdateTimer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                if (chartAutoUpdate) {
                    generateInfectionChart();
                }
            }
        }, 1000, 1000); // Update every second
    }
    
    private void refreshNodes() {
        SwingUtilities.invokeLater(() -> {
            nodesPanel.removeAll();
            
            for (int nodeId = 0; nodeId < numberOfNodes; nodeId++) {
                JButton nodeButton = createNodeButton(nodeId);
                nodesPanel.add(nodeButton);
            }
            
            nodesPanel.revalidate();
            nodesPanel.repaint();
        });
    }
    
    private JButton createNodeButton(int nodeId) {
        JButton button = new JButton("Node " + nodeId);
        
        List<InfectionRecord> infections = infectionHistory.getOrDefault(nodeId, Collections.emptyList());
        boolean isInfected = !infections.isEmpty();
        boolean isSource = sourceNodes.containsKey(nodeId);
        boolean hasRemovedMessages = remotionHistory.containsKey(nodeId) && 
                                     !remotionHistory.get(nodeId).isEmpty();
        
        // Color coding
        if (hasRemovedMessages) {
            int removedCount = remotionHistory.get(nodeId).size();
            button.setBackground(new Color(255, 100, 100)); // Light red
            button.setToolTipText("REMOVED (" + removedCount + " message(s) removed)");
        } else if (!isInfected) {
            button.setBackground(new Color(200, 200, 200)); // Light gray
            button.setToolTipText("SUSCEPTIBLE (not infected)");
        } else if (isSource) {
            button.setBackground(new Color(144, 238, 144)); // Light green
            button.setToolTipText("SOURCE: " + sourceNodes.get(nodeId));
        } else {
            button.setBackground(new Color(173, 216, 230)); // Light blue
            button.setToolTipText("INFECTED (FORWARDER)");
        }
        
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
        List<InfectionRecord> infections = infectionHistory.getOrDefault(nodeId, Collections.emptyList());
        boolean isSource = sourceNodes.containsKey(nodeId);
        
        SwingUtilities.invokeLater(() -> {
            selectedNodeLabel.setText("Node " + nodeId + 
                (isSource ? " (SOURCE: " + sourceNodes.get(nodeId) + ")" : 
                 infections.isEmpty() ? " (SUSCEPTIBLE)" : " (INFECTED)"));
            
            StringBuilder sb = new StringBuilder();
            sb.append("=== Node ").append(nodeId).append(" Status ===\n");
            sb.append("Status: ");
            if (infections.isEmpty()) {
                sb.append("SUSCEPTIBLE (not infected yet)\n");
            } else if (isSource) {
                sb.append("SOURCE (").append(sourceNodes.get(nodeId)).append(")\n");
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
                sb.append(String.format("%-15s %-12s %-10s %-10s %-10s\n", 
                    "Subject", "Timestamp", "SourceId", "Round", "Data"));
                sb.append("------------------------------------------------------------\n");
                
                Map<String, InfectionRecord> latestBySubjectSource = infections.stream()
                    .collect(Collectors.toMap(
                        inf -> inf.getSubject() + ":" + inf.getSourceId(),
                        inf -> inf,
                        (inf1, inf2) -> inf1.getTimestamp() > inf2.getTimestamp() ? inf1 : inf2
                    ));
                
                for (InfectionRecord record : latestBySubjectSource.values()) {
                    sb.append(String.format("%-15s %-12d %-10d %-10d %-10s\n",
                        record.getSubject(),
                        record.getTimestamp(),
                        record.getSourceId(),
                        record.getRound(),
                        record.getData()));
                }
            }
            
            messagesArea.setText(sb.toString());
        });
    }
    
    public void stop() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
        if (chartUpdateTimer != null) {
            chartUpdateTimer.cancel();
        }
        chartAutoUpdate = false;
    }
}
