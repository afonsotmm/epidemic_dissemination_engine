package general.communication.implementation;

import general.communication.Communication;
import general.communication.utils.Address;

public class TcpCommunication implements Communication {

    @Override
    public void startListening(Address myAddress) {}

    @Override
    public void sendMessage(Address destination, String message) {}

    @Override
    public String receiveMessage() { return null; }

    @Override
    public void closeSocket() {}
}
