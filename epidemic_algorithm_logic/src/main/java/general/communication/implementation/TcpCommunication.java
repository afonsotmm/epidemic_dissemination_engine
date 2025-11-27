package general.communication.implementation;

import general.communication.Communication;
import general.communication.utils.Address;

public class TcpCommunication implements Communication {

    void startListening(Address myAddress) {}

    void sendMessage(Address destination, String message) {}

    String receiveMessage() { return null; }
}
