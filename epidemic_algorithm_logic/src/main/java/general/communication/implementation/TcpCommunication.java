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
            java.net.InetAddress bindAddress = java.net.InetAddress.getByName(myAddress.getIp());
            this.serverSocket = new ServerSocket(myAddress.getPort(), 0, bindAddress);
            System.out.println("TCP Server listening on " + myAddress.getIp() + ":" + myAddress.getPort());

            serverThread = Thread.startVirtualThread(this::acceptConnections);
        } catch (IOException e) {
            System.err.println("Error creating TCP server socket on " + myAddress.getIp() + ":" + myAddress.getPort()
                    + ": " + e.getMessage());

            if (!e.getMessage().contains("Address already in use") && !e.getMessage().contains("already in use")) {
                e.printStackTrace();
            }
        }
    }

    private void acceptConnections() {
        while (isRunning && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println("TCP connection accepted from " + clientSocket.getRemoteSocketAddress());

                Thread.startVirtualThread(() -> handleClientConnection(clientSocket));
            } catch (IOException e) {
                if (isRunning) {
                    System.err.println("Error accepting TCP connection: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    private void handleClientConnection(Socket socket) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            String line;

            while ((line = reader.readLine()) != null && isRunning) {
                if (line.trim().isEmpty()) {
                    continue; // Skip empty lines
                }

                String message = line.trim();

                if (message.startsWith("{") && message.endsWith("}")) {
                    receivedMessages.offer(message);
                    System.out.println("TCP message received from " + socket.getRemoteSocketAddress());
                } else {
                    System.out.println("TCP: Received incomplete or invalid message: " + message);
                }
            }
        } catch (SocketException e) {
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
            socket = new Socket();
            socket.setSoTimeout(5000); // Read timeout
            socket.connect(new java.net.InetSocketAddress(destination.getIp(), destination.getPort()), 2000);

            PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                    true);

            writer.println(message);
            writer.flush();

        } catch (java.net.ConnectException e) {
            System.err.println("TCP Connection refused to " + destination.getIp() + ":" + destination.getPort());
        } catch (IOException e) {
            System.err.println("Error sending TCP message to " + destination.getIp() + ":" + destination.getPort()
                    + ": " + e.getMessage());
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
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
