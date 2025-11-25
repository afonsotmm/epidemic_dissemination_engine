package general.communication;

public class Address {

    private final String ip;
    private final Integer port;

    // Constructor
    public Address(String ip, Integer port) {
        this.ip = ip;
        this.port = port;
    }

    // Getters
    public String getIp() { return ip; }
    public Integer getPort() { return port; }

}
