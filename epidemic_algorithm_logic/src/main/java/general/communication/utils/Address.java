package general.communication.utils;

public class Address {

    private final String ip;
    private final int port;

    // Constructor
    public Address(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    // Getters
    public String getIp() { return ip; }
    public int getPort() { return port; }

}
