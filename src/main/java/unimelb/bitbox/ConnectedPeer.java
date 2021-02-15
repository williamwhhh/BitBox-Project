package unimelb.bitbox;

import unimelb.bitbox.util.Document;

import java.util.Objects;

/**
 * Represent a peer by recording the host address and port number.
 */
public class ConnectedPeer extends Object {

    public String host;
    public int port;

    /**
     * Constructor
     * @param host host address
     * @param port port number
     */
    public ConnectedPeer(String host, int port){
        this.host = host;
        this.port = port;
    }


    /**
     * Convert a string as host:port to Connected Peer.
     * @param hostport The string need to be converted.
     * @return The corresponding ConnectedPeer object.
     */
    public static ConnectedPeer convertConnectedPeer(String hostport){
        try {
            String[] hp = hostport.split(":");
            String host = hp[0];
            int port = Integer.parseInt(hp[1]);
            return new ConnectedPeer(host, port);
        } catch (Exception e){
            return null;
        }
    }

    /**
     * Convert to a Document
     * @return corresponding Document object
     */
    public Document toDoc(){
        Document doc = new Document();
        doc.append("host", host);
        doc.append("port", port);
        return doc;
    }

    /**
     * Override equals, comparing the values of host and port.
     * @param peer
     * @return
     */
    @Override
    public boolean equals(Object peer){
        if(peer instanceof ConnectedPeer){
            return ((ConnectedPeer) peer).host.equals(this.host) && ((ConnectedPeer) peer).port == this.port;
        }
        else{
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port);
    }
}
