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
    private Thread udpListenerThread;
    private Thread tcpListenerThread;

    public Listener(Supervisor supervisor, BlockingQueue<String> udpMsgsQueue, BlockingQueue<String> tcpMsgsQueue) {
        this.nodeCommunication = supervisor.getNodeCommunication();
        this.uiCommunication = supervisor.getUiCommunication();
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
        System.out.println("[Listener] TCP listener thread started");
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
     * TCP listening loop - polls TCP queue (non-blocking)
     */
    private void tcpListeningLoop() {
        System.out.println("[Listener] TCP listening loop started");
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
                System.out.println("[Listener] TCP listener thread interrupted");
                break;
            } catch (Exception e) {
                System.err.println("[Listener] Error in TCP listening loop: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
