package unimelb.bitbox;

import unimelb.bitbox.protocol.Protocol;

import java.io.BufferedWriter;
import java.util.ArrayList;

public interface CommunicationModule {
    ArrayList<ConnectedPeer> getConnectedPeers();
    boolean disconnectPeer(ConnectedPeer p);
    void initHandshake(ConnectedPeer p, BufferedWriter out, String secretKey);
    void initHandshake(ConnectedPeer p);
    void broadcast(Protocol protocol);
}
