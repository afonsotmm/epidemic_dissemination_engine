package supervisor.communication;

import general.communication.Communication;
import supervisor.Supervisor;

import java.util.concurrent.BlockingQueue;

/**	
 * Responsible for receiving messages from the communication layer (UDP for nodes, TCP for UI)
 * and putting them in separate queues.
 * Uses two separate threads: one for UDP (nodes) and one for TCP (UI).
 */

public class Listener {
    private BlockingQueue<String> udpMsgsQueue;  // Queue for UDP messages (from nodes)
    private BlockingQueue<String> tcpMsgsQueue;  // Queue for TCP messages (from UI)
    private Communication nodeCommunication;  // UDP for nodes
    private Communication uiCommunication;     // TCP for UI
    private Communication nodeTcpCommunication; // TCP for nodes (distributed mode)
    private Thread udpListenerThread;
    private Thread tcpListenerThread;
    private Thread nodeTcpListenerThread; // Thread for TCP messages from nodes (distributed mode)

    private Supervisor supervisor; // Keep reference to supervisor to get nodeTcpCommunication dynamically
    
    public Listener(Supervisor supervisor, BlockingQueue<String> udpMsgsQueue, BlockingQueue<String> tcpMsgsQueue) {
        this.supervisor = supervisor;
        this.nodeCommunication = supervisor.getNodeCommunication();
        this.uiCommunication = supervisor.getUiCommunication();
        // nodeTcpCommunication will be retrieved dynamically in startListening()
        this.udpMsgsQueue = udpMsgsQueue;
        this.tcpMsgsQueue = tcpMsgsQueue;
    }

    /**
     * Start both listener threads (UDP and TCP)
     */
    public void startListening() {
        System.out.println("[Listener] Starting UDP and TCP listener threads...");
        
        // Start UDP listener thread (for nodes)
        udpListenerThread = Thread.startVirtualThread(this::udpListeningLoop);
        System.out.println("[Listener] UDP listener thread started");
        
        // Start TCP listener thread (for UI)
        tcpListenerThread = Thread.startVirtualThread(this::tcpListeningLoop);
        System.out.println("[Listener] TCP listener thread started for UI");
        
        // TCP listener for nodes will be started later when nodeTcpCommunication is created (in distributed mode)
        // We check for it dynamically in nodeTcpListeningLoop if needed
    }

    /**
     * UDP listening loop - blocks on UDP socket.receive()
     */
    private void udpListeningLoop() {
        System.out.println("[Listener] UDP listening loop started");
        while (true) {
            try {
                String nodeMsg = nodeCommunication.receiveMessage();
                if (nodeMsg != null) {
                    System.out.println("[Listener] Received message from Node (UDP)");
                    udpMsgsQueue.put(nodeMsg);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("[Listener] UDP listener thread interrupted");
                break;
            } catch (Exception e) {
                System.err.println("[Listener] Error in UDP listening loop: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * TCP listening loop - polls TCP queue (non-blocking) for UI messages
     */
    private void tcpListeningLoop() {
        System.out.println("[Listener] TCP listening loop started (UI)");
        while (true) {
            try {
                String uiMsg = uiCommunication.receiveMessage();
                if (uiMsg != null) {
                    System.out.println("[Listener] Received message from UI (TCP), length: " + uiMsg.length() + " chars");
                    tcpMsgsQueue.put(uiMsg);
                }
                // Small sleep to avoid busy-waiting
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("[Listener] TCP listener thread interrupted (UI)");
                break;
            } catch (Exception e) {
                System.err.println("[Listener] Error in TCP listening loop (UI): " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Start TCP listener thread for nodes (distributed mode)
     * Called after nodeTcpCommunication is created in startDistributedNetwork()
     */
    public void startNodeTcpListening() {
        if (nodeTcpListenerThread == null || !nodeTcpListenerThread.isAlive()) {
            this.nodeTcpCommunication = supervisor.getNodeTcpCommunication();
            if (nodeTcpCommunication != null) {
                nodeTcpListenerThread = Thread.startVirtualThread(this::nodeTcpListeningLoop);
                System.out.println("[Listener] TCP listener thread started for nodes (distributed mode)");
            }
        }
    }
    
    /**
     * TCP listening loop for nodes (distributed mode) - polls TCP queue (non-blocking)
     */
    private void nodeTcpListeningLoop() {
        System.out.println("[Listener] TCP listening loop started (nodes)");
        while (true) {
            try {
                // Get nodeTcpCommunication dynamically (in case it's created after listener starts)
                if (nodeTcpCommunication == null) {
                    nodeTcpCommunication = supervisor.getNodeTcpCommunication();
                    if (nodeTcpCommunication == null) {
                        // Wait a bit before checking again
                        Thread.sleep(100);
                        continue;
                    }
                }
                
                String nodeMsg = nodeTcpCommunication.receiveMessage();
                if (nodeMsg != null) {
                    System.out.println("[Listener] Received message from Node (TCP)");
                    udpMsgsQueue.put(nodeMsg); // Put in UDP queue - Dispatcher will route based on direction
                }
                // Small sleep to avoid busy-waiting
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("[Listener] TCP listener thread interrupted (nodes)");
                break;
            } catch (Exception e) {
                System.err.println("[Listener] Error in TCP listening loop (nodes): " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
