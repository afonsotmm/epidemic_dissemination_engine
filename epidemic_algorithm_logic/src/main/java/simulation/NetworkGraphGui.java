package simulation;

import epidemic_core.message.supervisor_to_ui.structural_infos.StructuralInfosMsg;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.*;
import java.util.List;

/**
 * GUI for visualizing the network topology as a graph.
 * Nodes change color when infected.
 */
public class NetworkGraphGui extends JFrame {
    
    private GraphPanel graphPanel;
    private JLabel statusLabel;
    private Map<Integer, NodePosition> nodes;
    private Map<Integer, Set<Integer>> edges;
    private Map<Integer, Boolean> infectedNodes;
    private Map<Integer, String> sourceNodes; // nodeId -> subject

    private Map<EdgeKey, Long> flashingEdges;
    private static final long ANIMATION_DURATION_MS = 500;
    
    private Runnable onStartCallback;
    private Runnable onEndCallback;
    
    private static final int NODE_RADIUS = 15;
    private static final Color COLOR_SUSCEPTIBLE = Color.LIGHT_GRAY;
    private static final Color COLOR_INFECTED = Color.RED;
    private static final Color COLOR_SOURCE = Color.BLUE;
    private static final Color COLOR_FLASHING_EDGE = Color.ORANGE;
    
    public NetworkGraphGui() {
        super("Network Topology Visualization");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1400, 900);
        
        nodes = new HashMap<>();
        edges = new HashMap<>();
        infectedNodes = new HashMap<>();
        sourceNodes = new HashMap<>();
        flashingEdges = new HashMap<>();

        javax.swing.Timer animationTimer = new javax.swing.Timer(50, e -> {
            long currentTime = System.currentTimeMillis();
            flashingEdges.entrySet().removeIf(entry -> 
                currentTime - entry.getValue() > ANIMATION_DURATION_MS
            );
            graphPanel.repaint();
        });
        animationTimer.start();

        graphPanel = new GraphPanel();
        statusLabel = new JLabel("Waiting for network topology...");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton startButton = new JButton("Start Network");
        startButton.setFont(new Font("Arial", Font.BOLD, 14));
        startButton.setBackground(new Color(76, 175, 80));
        startButton.setForeground(Color.WHITE);
        startButton.addActionListener(e -> {
            if (onStartCallback != null) {
                onStartCallback.run();
            }
        });
        
        JButton endButton = new JButton("End Network");
        endButton.setFont(new Font("Arial", Font.BOLD, 14));
        endButton.setBackground(new Color(244, 67, 54));
        endButton.setForeground(Color.WHITE);
        endButton.addActionListener(e -> {
            if (onEndCallback != null) {
                onEndCallback.run();
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        buttonPanel.add(startButton);
        buttonPanel.add(endButton);

        setLayout(new BorderLayout());
        add(graphPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.NORTH);
        add(statusLabel, BorderLayout.SOUTH);
        
        setLocationRelativeTo(null);
    }
    
    public void setStartCallback(Runnable callback) {
        this.onStartCallback = callback;
    }
    
    public void setEndCallback(Runnable callback) {
        this.onEndCallback = callback;
    }

    public void updateTopology(StructuralInfosMsg structuralMsg) {
        SwingUtilities.invokeLater(() -> {
            nodes.clear();
            edges.clear();
            sourceNodes.clear();
            
            List<StructuralInfosMsg.NodeInfo> nodeInfos = structuralMsg.getNodes();

            for (StructuralInfosMsg.NodeInfo nodeInfo : nodeInfos) {
                int nodeId = nodeInfo.getId();
                List<Integer> neighbors = nodeInfo.getNeighbors();
                String subject = nodeInfo.getSubject();

                nodes.put(nodeId, new NodePosition(0, 0)); // Position will be calculated

                edges.put(nodeId, new HashSet<>(neighbors));

                if (subject != null) {
                    sourceNodes.put(nodeId, subject);
                    infectedNodes.put(nodeId, true); // Source nodes start infected
                } else {
                    infectedNodes.put(nodeId, false);
                }
            }

            calculateCircularLayout();
            
            statusLabel.setText("Network: " + nodes.size() + " nodes, " + countEdges() + " edges");
            graphPanel.repaint();
        });
    }

    public void updateNodeInfection(int nodeId, boolean infected) {
        SwingUtilities.invokeLater(() -> {
            if (nodes.containsKey(nodeId)) {
                infectedNodes.put(nodeId, infected);
                graphPanel.repaint();
            }
        });
    }

    public void flashEdge(int fromNodeId, int toNodeId) {
        SwingUtilities.invokeLater(() -> {
            if (!nodes.containsKey(fromNodeId) || !nodes.containsKey(toNodeId)) {
                return;
            }

            boolean edgeExists = false;
            Set<Integer> neighborsFrom = edges.get(fromNodeId);
            if (neighborsFrom != null && neighborsFrom.contains(toNodeId)) {
                edgeExists = true;
            } else {
                Set<Integer> neighborsTo = edges.get(toNodeId);
                if (neighborsTo != null && neighborsTo.contains(fromNodeId)) {
                    edgeExists = true;
                }
            }
            
            if (edgeExists) {
                EdgeKey edge = new EdgeKey(
                    Math.min(fromNodeId, toNodeId),
                    Math.max(fromNodeId, toNodeId)
                );

                flashingEdges.put(edge, System.currentTimeMillis());
                System.out.println("Flashing edge: " + fromNodeId + " -> " + toNodeId);
                graphPanel.repaint();
            } else {
                System.out.println("Edge not found: " + fromNodeId + " -> " + toNodeId);
            }
        });
    }

    private void calculateCircularLayout() {
        if (nodes.isEmpty()) return;
        
        int nodeCount = nodes.size();
        int centerX = 600;
        int centerY = 350;
        int radius = Math.max(300, nodeCount * 30);
        
        List<Integer> nodeIds = new ArrayList<>(nodes.keySet());
        Collections.sort(nodeIds);
        
        for (int i = 0; i < nodeIds.size(); i++) {
            int nodeId = nodeIds.get(i);
            double angle = 2 * Math.PI * i / nodeCount;
            int x = (int) (centerX + radius * Math.cos(angle));
            int y = (int) (centerY + radius * Math.sin(angle));
            nodes.put(nodeId, new NodePosition(x, y));
        }
    }
    
    private int countEdges() {
        int count = 0;
        for (Set<Integer> neighbors : edges.values()) {
            count += neighbors.size();
        }
        return count / 2; // Each edge counted twice
    }

    private class GraphPanel extends JPanel {
        
        public GraphPanel() {
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(1400, 800));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int x = e.getX();
                    int y = e.getY();
                    
                    // Find clicked node
                    for (Map.Entry<Integer, NodePosition> entry : nodes.entrySet()) {
                        int nodeId = entry.getKey();
                        NodePosition pos = entry.getValue();
                        
                        double dx = x - pos.x;
                        double dy = y - pos.y;
                        double distance = Math.sqrt(dx * dx + dy * dy);
                        
                        if (distance <= NODE_RADIUS) {
                            showNodeInfo(nodeId);
                            break;
                        }
                    }
                }
            });
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            long currentTime = System.currentTimeMillis();
            for (Map.Entry<Integer, Set<Integer>> entry : edges.entrySet()) {
                int fromNode = entry.getKey();
                NodePosition fromPos = nodes.get(fromNode);
                if (fromPos == null) continue;
                
                for (int toNode : entry.getValue()) {
                    NodePosition toPos = nodes.get(toNode);
                    if (toPos == null || toNode < fromNode) continue; // Draw each edge once

                    EdgeKey edge = new EdgeKey(Math.min(fromNode, toNode), Math.max(fromNode, toNode));
                    Long flashStartTime = flashingEdges.get(edge);
                    
                    if (flashStartTime != null && (currentTime - flashStartTime) < ANIMATION_DURATION_MS) {
                        double progress = (double)(currentTime - flashStartTime) / ANIMATION_DURATION_MS;

                        int alpha = (int)(255 * (1.0 - Math.abs(progress - 0.5) * 2));
                        alpha = Math.max(50, Math.min(255, alpha));

                        g2.setColor(new Color(COLOR_FLASHING_EDGE.getRed(), 
                                            COLOR_FLASHING_EDGE.getGreen(), 
                                            COLOR_FLASHING_EDGE.getBlue(), 
                                            alpha));
                        g2.setStroke(new BasicStroke(3.0f)); // Thicker line for flashing edges
                    } else {
                        g2.setColor(Color.GRAY);
                        g2.setStroke(new BasicStroke(1.0f));
                    }
                    
                    g2.draw(new Line2D.Double(fromPos.x, fromPos.y, toPos.x, toPos.y));
                }
            }

            for (Map.Entry<Integer, NodePosition> entry : nodes.entrySet()) {
                int nodeId = entry.getKey();
                NodePosition pos = entry.getValue();

                Color nodeColor;
                if (sourceNodes.containsKey(nodeId)) {
                    nodeColor = COLOR_SOURCE;
                } else if (infectedNodes.getOrDefault(nodeId, false)) {
                    nodeColor = COLOR_INFECTED;
                } else {
                    nodeColor = COLOR_SUSCEPTIBLE;
                }

                g2.setColor(nodeColor);
                g2.fill(new Ellipse2D.Double(pos.x - NODE_RADIUS, pos.y - NODE_RADIUS, 
                                             NODE_RADIUS * 2, NODE_RADIUS * 2));

                g2.setColor(Color.BLACK);
                g2.setStroke(new BasicStroke(2.0f));
                g2.draw(new Ellipse2D.Double(pos.x - NODE_RADIUS, pos.y - NODE_RADIUS, 
                                             NODE_RADIUS * 2, NODE_RADIUS * 2));

                g2.setColor(Color.BLACK);
                g2.setFont(new Font("Arial", Font.BOLD, 10));
                String label = String.valueOf(nodeId);
                FontMetrics fm = g2.getFontMetrics();
                int labelWidth = fm.stringWidth(label);
                int labelHeight = fm.getHeight();
                g2.drawString(label, pos.x - labelWidth / 2, pos.y + labelHeight / 4);
            }

            drawLegend(g2);
        }
        
        private void drawLegend(Graphics2D g2) {
            int x = 20;
            int y = 20;
            int boxSize = 15;
            int spacing = 25;
            
            g2.setFont(new Font("Arial", Font.PLAIN, 12));

            g2.setColor(COLOR_SUSCEPTIBLE);
            g2.fillRect(x, y, boxSize, boxSize);
            g2.setColor(Color.BLACK);
            g2.drawRect(x, y, boxSize, boxSize);
            g2.drawString("Susceptible", x + boxSize + 5, y + 12);

            g2.setColor(COLOR_INFECTED);
            g2.fillRect(x, y + spacing, boxSize, boxSize);
            g2.setColor(Color.BLACK);
            g2.drawRect(x, y + spacing, boxSize, boxSize);
            g2.drawString("Infected", x + boxSize + 5, y + spacing + 12);

            g2.setColor(COLOR_SOURCE);
            g2.fillRect(x, y + spacing * 2, boxSize, boxSize);
            g2.setColor(Color.BLACK);
            g2.drawRect(x, y + spacing * 2, boxSize, boxSize);
            g2.drawString("Source", x + boxSize + 5, y + spacing * 2 + 12);
        }
        
        private void showNodeInfo(int nodeId) {
            StringBuilder info = new StringBuilder();
            info.append("Node ").append(nodeId);
            
            if (sourceNodes.containsKey(nodeId)) {
                info.append(" (SOURCE: ").append(sourceNodes.get(nodeId)).append(")");
            }
            
            if (infectedNodes.getOrDefault(nodeId, false)) {
                info.append(" - INFECTED");
            } else {
                info.append(" - SUSCEPTIBLE");
            }
            
            Set<Integer> neighbors = edges.get(nodeId);
            if (neighbors != null) {
                info.append("\nNeighbors: ").append(neighbors.size());
            }
            
            statusLabel.setText(info.toString());
        }
    }

    private static class NodePosition {
        int x, y;
        
        NodePosition(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private static class EdgeKey {
        final int from;
        final int to;
        
        EdgeKey(int from, int to) {
            this.from = from;
            this.to = to;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EdgeKey edgeKey = (EdgeKey) o;
            return from == edgeKey.from && to == edgeKey.to;
        }
        
        @Override
        public int hashCode() {
            return from * 31 + to;
        }
    }
}
