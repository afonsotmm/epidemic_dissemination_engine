package general.communication.implementation;

import general.communication.Communication;
import general.communication.utils.Address;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class UdpCommunication implements Communication {

    private DatagramSocket socket;
    private static final int BUFFER_SIZE = 1024;

    @Override
    public void setupSocket(Address myAddress) {
        try {
            // Bind to specific IP address and port to allow multiple sockets on same port with different IPs
            InetAddress bindAddress = InetAddress.getByName(myAddress.getIp());
            this.socket = new DatagramSocket(myAddress.getPort(), bindAddress);
            // UDP socket can block - we have a dedicated thread for it
            System.out.println("UDP Socket listening on " + myAddress.getIp() + ":" + myAddress.getPort());
        } catch (SocketException e) {
            System.err.println("Error creating UDP socket on " + myAddress.getIp() + ":" + myAddress.getPort() + ": " + e.getMessage());
            // Don't print full stack trace for "Address already in use" - it's expected with many nodes
            if (!e.getMessage().contains("Address already in use")) {
                e.printStackTrace();
            }
            // Socket remains null, which will be checked in sendMessage/receiveMessage
        } catch (IOException e) {
            System.err.println("Error resolving IP address " + myAddress.getIp() + ": " + e.getMessage());
            e.printStackTrace();
            // Socket remains null
        }
    }
    
    public boolean isSocketReady() {
        return socket != null && !socket.isClosed();
    }

    @Override
    public void sendMessage(Address destination, String message) {
        if (!isSocketReady()) {
            // Silently fail - socket may not be ready yet or failed to initialize
            return;
        }

        try {
            // Convert string to bytes
            byte[] messageBytes = message.getBytes();
            
            // Created an UDP packet with the destination address
            InetAddress destAddress = InetAddress.getByName(destination.getIp());
            DatagramPacket packet = new DatagramPacket(
                messageBytes, 
                messageBytes.length, 
                destAddress, 
                destination.getPort()
            );
            
            // Send packet
            socket.send(packet);
            System.out.println("UDP message sent to " + destination.getIp() + ":" + destination.getPort());
        } catch (IOException e) {
            System.err.println("Error sending UDP message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Send message via UDP broadcast (to 255.255.255.255)
    public void sendBroadcastMessage(int port, String message) {
        if (!isSocketReady()) {
            // Silently fail - socket may not be ready yet or failed to initialize
            return;
        }

        try {
            // Enable broadcast mode
            socket.setBroadcast(true);
            
            // Convert string to bytes
            byte[] messageBytes = message.getBytes();
            
            // Create UDP packet with broadcast address
            InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");
            DatagramPacket packet = new DatagramPacket(
                messageBytes, 
                messageBytes.length, 
                broadcastAddress, 
                port
            );
            
            // Send packet
            socket.send(packet);
            System.out.println("UDP broadcast message sent to 255.255.255.255:" + port);
        } catch (IOException e) {
            System.err.println("Error sending UDP broadcast message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public String receiveMessage() {
        if (!isSocketReady()) {
            // Return null if socket not ready - will be retried in next cycle
            return null;
        }

        try {
            // Creation of a buffer to the received messages
            byte[] buffer = new byte[BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            
            // Blocks here until it receives (now we have a dedicated thread for UDP)
            socket.receive(packet);
            
            // Extract the received message
            String receivedMessage = new String(packet.getData(), 0, packet.getLength());
            System.out.println("UDP message received from " + packet.getAddress().getHostAddress() + ":" + packet.getPort());
            
            return receivedMessage;
        } catch (IOException e) {
            System.err.println("Error receiving UDP message: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    @Override
    public void closeSocket() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
            System.out.println("UDP socket closed");
        }
    }
}