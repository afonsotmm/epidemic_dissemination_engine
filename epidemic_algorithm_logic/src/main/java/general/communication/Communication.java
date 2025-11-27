package general.communication;

import general.communication.utils.Address;

public interface Communication {
    // Socket configuration
    void startListening(Address myAddress);

    // Send message to the dest. address
    void sendMessage(Address destination, String message);

    // Receive the message (blocks until it receives)
    String receiveMessage();
}
