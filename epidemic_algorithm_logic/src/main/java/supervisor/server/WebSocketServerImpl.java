package supervisor.server;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;

public class WebSocketServerImpl extends WebSocketServer {

    private final BlockingQueue<String> uiQueue;

    public WebSocketServerImpl(int port, BlockingQueue<String> uiQueue) {
        super(new InetSocketAddress(port));
        this.uiQueue = uiQueue;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("[WS] Client connected: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("[WS] Client disconnected: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("[WS] Received message: " + message);
        if (uiQueue != null) {
            uiQueue.offer(message);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("[WS] Error: " + ex.getMessage());
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("[WS] WebSocket Server started on port " + getPort());
    }
}
