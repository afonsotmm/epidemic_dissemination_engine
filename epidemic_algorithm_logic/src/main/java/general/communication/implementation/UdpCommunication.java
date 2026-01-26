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
            InetAddress bindAddress = InetAddress.getByName(myAddress.getIp());
            this.socket = new DatagramSocket(myAddress.getPort(), bindAddress);

            System.out.println("UDP Socket listening on " + myAddress.getIp() + ":" + myAddress.getPort());
        } catch (SocketException e) {
            System.err.println("Error creating UDP socket on " + myAddress.getIp() + ":" + myAddress.getPort() + ": " + e.getMessage());

            if (!e.getMessage().contains("Address already in use")) {
                e.printStackTrace();
            }

        } catch (IOException e) {
            System.err.println("Error resolving IP address " + myAddress.getIp() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public boolean isSocketReady() {
        return socket != null && !socket.isClosed();
    }

    @Override
    public void sendMessage(Address destination, String message) {
        if (!isSocketReady()) {
            return;
        }

        try {
            byte[] messageBytes = message.getBytes();

            InetAddress destAddress = InetAddress.getByName(destination.getIp());
            DatagramPacket packet = new DatagramPacket(
                messageBytes, 
                messageBytes.length, 
                destAddress, 
                destination.getPort()
            );

            socket.send(packet);
            System.out.println("UDP message sent to " + destination.getIp() + ":" + destination.getPort());
        } catch (IOException e) {
            System.err.println("Error sending UDP message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendBroadcastMessage(int port, String message) {
        if (!isSocketReady()) {
            return;
        }

        try {
            socket.setBroadcast(true);

            byte[] messageBytes = message.getBytes();

            InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");
            DatagramPacket packet = new DatagramPacket(
                messageBytes, 
                messageBytes.length, 
                broadcastAddress, 
                port
            );

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
            return null;
        }

        try {
            byte[] buffer = new byte[BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            socket.receive(packet);

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