package general.communication.utils;

public class Address {

    private final String ip;
    private final int port;

    // Constructor
    public Address(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public static Address parse(String value) {
        String[] parts = value.split(":");
        String ip = parts[0];
        int port = Integer.parseInt(parts[1]);
        return new Address(ip, port);
    }

    // Getters
    public String getIp() { return ip; }
    public int getPort() { return port; }

}
