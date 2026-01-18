package general.communication.implementation;

import general.communication.Communication;
import general.communication.utils.Address;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TcpCommunication implements Communication {

    private ServerSocket serverSocket;
    private BlockingQueue<String> receivedMessages;
    private Thread serverThread;
    private volatile boolean isRunning = false;

    @Override
    public void setupSocket(Address myAddress) {
        this.receivedMessages = new LinkedBlockingQueue<>();
        this.isRunning = true;
        
        try {
            // Bind to specific IP address to allow multiple sockets on same port with different IPs
            // Similar to UdpCommunication - bind to specific InetAddress instead of 0.0.0.0
            java.net.InetAddress bindAddress = java.net.InetAddress.getByName(myAddress.getIp());
            // ServerSocket(port, backlog, bindAddress) - backlog 0 uses system default
            this.serverSocket = new ServerSocket(myAddress.getPort(), 0, bindAddress);
            System.out.println("TCP Server listening on " + myAddress.getIp() + ":" + myAddress.getPort());
            
            // Start thread to accept connections
            serverThread = Thread.startVirtualThread(this::acceptConnections);
        } catch (IOException e) {
            System.err.println("Error creating TCP server socket on " + myAddress.getIp() + ":" + myAddress.getPort() + ": " + e.getMessage());
            // Don't print full stack trace for "Address already in use" - it's expected with many nodes if IP is not properly bound
            if (!e.getMessage().contains("Address already in use") && !e.getMessage().contains("already in use")) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Accept incoming TCP connections and handle them
     */
    private void acceptConnections() {
        while (isRunning && serverSocket != null && !serverSocket.isClosed()) {
            try {
                // Accept a new connection
                Socket clientSocket = serverSocket.accept();
                System.out.println("TCP connection accepted from " + clientSocket.getRemoteSocketAddress());
                
                // Handle this connection in a separate thread
                Thread.startVirtualThread(() -> handleClientConnection(clientSocket));
            } catch (IOException e) {
                if (isRunning) {
                    System.err.println("Error accepting TCP connection: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Handle a client connection - read messages and put them in the queue
     */
    private void handleClientConnection(Socket socket) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            
            String line;
            
            // Read messages line by line (JSON messages are single-line)
            while ((line = reader.readLine()) != null && isRunning) {
                if (line.trim().isEmpty()) {
                    continue; // Skip empty lines
                }
                
                // Trim the line to remove any whitespace
                String message = line.trim();
                
                // Check if we have a complete JSON message
                if (message.startsWith("{") && message.endsWith("}")) {
                    // Complete JSON message received
                    receivedMessages.offer(message);
                    System.out.println("TCP message received from " + socket.getRemoteSocketAddress());
                } else {
                    System.out.println("TCP: Received incomplete or invalid message: " + message);
                }
            }
        } catch (SocketException e) {
            // Connection closed - this is normal
            if (isRunning) {
                System.out.println("TCP connection closed: " + socket.getRemoteSocketAddress());
            }
        } catch (IOException e) {
            System.err.println("Error reading from TCP connection: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }

    public boolean isSocketReady() {
        return serverSocket != null && !serverSocket.isClosed() && isRunning;
    }

    @Override
    public void sendMessage(Address destination, String message) {
        Socket socket = null;
        try {
            // Create a new socket connection for each message (TCP is connection-oriented)
            socket = new Socket(destination.getIp(), destination.getPort());
            
            // Send message
            PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), 
                true
            );
            
            writer.println(message);
            writer.flush();
            
            // Success - message sent (commented out to reduce log spam)
            // System.out.println("TCP message sent to " + destination.getIp() + ":" + destination.getPort());
        } catch (java.net.ConnectException e) {
            // Connection refused - UI might not be available, this is expected
            // Silently ignore to avoid log spam when UI is not running
            // Return false to indicate failure (but we can't change method signature)
        } catch (IOException e) {
            // Other IO errors - log but don't print full stack trace
            System.err.println("Error sending TCP message to " + destination.getIp() + ":" + destination.getPort() + ": " + e.getMessage());
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    @Override
    public String receiveMessage() {
        if (receivedMessages == null) {
            return null;
        }
        
        if (!isSocketReady()) {
            return null;
        }
        
        try {
            // Poll from queue (non-blocking)
            return receivedMessages.poll();
        } catch (Exception e) {
            System.err.println("[TcpCommunication] Error receiving TCP message: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void closeSocket() {
        isRunning = false;
        
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                System.out.println("TCP server socket closed");
            } catch (IOException e) {
                System.err.println("Error closing TCP server socket: " + e.getMessage());
            }
        }
    }
}
